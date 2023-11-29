/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.file.archive;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.gradle.api.GradleException;
import org.gradle.api.InvalidUserDataException;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FilePermissions;
import org.gradle.api.file.FileVisitor;
import org.gradle.api.file.LinksStrategy;
import org.gradle.api.file.RelativePath;
import org.gradle.api.file.SymbolicLinkDetails;
import org.gradle.api.internal.file.DefaultFilePermissions;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.provider.Provider;
import org.gradle.cache.internal.DecompressionCache;
import org.gradle.internal.file.Chmod;
import org.gradle.internal.hash.FileHasher;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static java.lang.String.format;

public class ZipFileTree extends AbstractArchiveFileTree {
    private final Provider<File> fileProvider;
    private final Chmod chmod;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileHasher fileHasher;

    public ZipFileTree(
        Provider<File> zipFile,
        Chmod chmod,
        DirectoryFileTreeFactory directoryFileTreeFactory,
        FileHasher fileHasher,
        DecompressionCache decompressionCache
    ) {
        super(decompressionCache);
        this.fileProvider = zipFile;
        this.chmod = chmod;
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileHasher = fileHasher;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }

    @Override
    public String getDisplayName() {
        return format("ZIP '%s'", fileProvider.getOrNull());
    }

    @Override
    public DirectoryFileTree getMirror() {
        return directoryFileTreeFactory.create(getExpandedDir());
    }

    @Override
    public void visit(FileVisitor visitor) {
        decompressionCache.useCache(() -> {
            File zipFile = fileProvider.get();
            if (!zipFile.exists()) {
                throw new InvalidUserDataException(format("Cannot expand %s as it does not exist.", getDisplayName()));
            }
            if (!zipFile.isFile()) {
                throw new InvalidUserDataException(format("Cannot expand %s as it is not a file.", getDisplayName()));
            }

            AtomicBoolean stopFlag = new AtomicBoolean();
            File expandedDir = getExpandedDir();
            LinksStrategy linksStrategy = visitor.linksStrategy();
            try (ZipFile zip = new ZipFile(zipFile)) {
                // The iteration order of zip.getEntries() is based on the hash of the zip entry. This isn't much use
                // to us. So, collect the entries in a map and iterate over them in alphabetical order.
                Iterator<ZipArchiveEntry> sortedEntries = entriesSortedByName(zip);
                while (!stopFlag.get() && sortedEntries.hasNext()) {
                    ZipArchiveEntry entry = sortedEntries.next();

                    visitEntry(entry, entry.getName(), zip, zipFile, visitor, stopFlag, expandedDir, linksStrategy);
                }
            } catch (GradleException e) {
                throw e;
            } catch (Exception e) {
                throw new GradleException(format("Cannot expand %s.", getDisplayName()), e);
            }
        });
    }

    private void visitEntry(ZipArchiveEntry entry, String targetPath, ZipFile zip, File zipFile, FileVisitor visitor, AtomicBoolean stopFlag, File expandedDir, LinksStrategy linksStrategy) {
        SymbolicLinkDetailsImpl linkDetails = null;
        if (entry.isUnixSymlink()) {
            linkDetails = new SymbolicLinkDetailsImpl(entry, zip);
        }
        boolean preserveLink = linksStrategy.shouldBePreserved(linkDetails, targetPath);
        DetailsImpl details = new DetailsImpl(zipFile, expandedDir, entry, targetPath, zip, stopFlag, chmod, linkDetails, preserveLink);
        if (details.isDirectory()) {
            visitor.visitDir(details);
            if (entry.isUnixSymlink()) {
                ZipArchiveEntry targetEntry = linkDetails.getTargetEntry();
                String originalPath = targetEntry.isDirectory() ? targetEntry.getName() : targetEntry.getName() + '/';
                visitRecursively(originalPath, targetPath + '/', zip, zipFile, visitor, stopFlag, expandedDir, linksStrategy);
            }
        } else {
            visitor.visitFile(details);
        }
    }

    private void visitRecursively(String originalPath, String targetPath, ZipFile zip, File zipFile, FileVisitor visitor, AtomicBoolean stopFlag, File expandedDir, LinksStrategy linksStrategy) {
        Iterator<ZipArchiveEntry> sortedEntries = entriesSortedByName(zip);
        while (!stopFlag.get() && sortedEntries.hasNext()) { // optimize by finding start
            ZipArchiveEntry subEntry = sortedEntries.next();
            String subEntryPath = subEntry.getName();
            if (!subEntryPath.startsWith(originalPath) || subEntryPath.length() <= originalPath.length() + 1) {
                continue;
            }

            String newPath = targetPath + subEntryPath.substring(originalPath.length());
            visitEntry(subEntry, newPath, zip, zipFile, visitor, stopFlag, expandedDir, linksStrategy);
        }
    }

    private Iterator<ZipArchiveEntry> entriesSortedByName(ZipFile zip) {
        Map<String, ZipArchiveEntry> entriesByName = new TreeMap<>();
        Enumeration<ZipArchiveEntry> entries = zip.getEntries();
        while (entries.hasMoreElements()) {
            ZipArchiveEntry entry = entries.nextElement();
            entriesByName.put(entry.getName(), entry);
        }
        return entriesByName.values().iterator();
    }

    @Override
    public Provider<File> getBackingFileProvider() {
        return fileProvider;
    }

    private File getExpandedDir() {
        File zipFile = fileProvider.get();
        String expandedDirName = "zip_" + fileHasher.hash(zipFile);
        return new File(decompressionCache.getBaseDir(), expandedDirName);
    }

    private static final class DetailsImpl extends AbstractArchiveFileTreeElement {
        private final File originalFile;
        private final ZipArchiveEntry entry;
        private final RelativePath relativePath;
        private final ZipFile zip;
        private final SymbolicLinkDetailsImpl linkDetails;
        private final boolean preserveLink;

        public DetailsImpl(
            File originalFile,
            File expandedDir,
            ZipArchiveEntry entry,
            String targetPath,
            ZipFile zip,
            AtomicBoolean stopFlag,
            Chmod chmod,
            @Nullable SymbolicLinkDetailsImpl linkDetails,
            boolean preserveLink
        ) {
            super(chmod, expandedDir, stopFlag);
            this.originalFile = originalFile;
            this.entry = entry;
            this.zip = zip;
            this.preserveLink = preserveLink;
            this.linkDetails = linkDetails;
            boolean isDirectory = entry.isDirectory() || (entry.isUnixSymlink() && !preserveLink && linkDetails.targetExists() && linkDetails.getTargetEntry().isDirectory());
            this.relativePath = new RelativePath(!isDirectory, targetPath.split("/"));
        }

        @Override
        public String getDisplayName() {
            return format("zip entry %s!%s", originalFile, entry.getName());
        }

        @Override
        protected String getEntryName() {
            return relativePath.getPathString();
        } //FIXME: refactor

        @Override
        protected ZipArchiveEntry getArchiveEntry() { //FIXME: refactor
            if (!preserveLink && entry.isUnixSymlink() && linkDetails.targetExists()) {
                return linkDetails.getTargetEntry();
            } else {
                return entry;
            }
        }

        @Override
        public boolean isSymbolicLink() {
            return preserveLink && entry.isUnixSymlink();
        }

        @Override
        public boolean isDirectory() {
            return !relativePath.isFile();
        }

        @Override
        public InputStream open() {
            if (!entry.isUnixSymlink()) {
                try {
                    return zip.getInputStream(entry);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            if (linkDetails.targetExists()) {
                try {
                    return zip.getInputStream(linkDetails.getTargetEntry());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }

            throw new GradleException(String.format("Couldn't follow symbolic link '%s' pointing to '%s'.", getRelativePath(), linkDetails.getTarget()));
        }

        @Override
        public FilePermissions getPermissions() {
            int unixMode = 0;
            if (entry.isUnixSymlink() && !preserveLink && linkDetails.targetExists()) {
                unixMode = linkDetails.getTargetEntry().getUnixMode() & 0777;
            } else {
                unixMode = entry.getUnixMode() & 0777;
            }
            if (unixMode != 0) {
                return new DefaultFilePermissions(unixMode);
            }

            return super.getPermissions();
        }

        @Override
        public long getLastModified() {
            if (!preserveLink && entry.isUnixSymlink() && linkDetails.targetExists()) {
                return linkDetails.targetEntry.getLastModifiedDate().getTime();
            } else {
                return entry.getLastModifiedDate().getTime();
            }
        }

        @Nullable
        @Override
        public SymbolicLinkDetails getSymbolicLinkDetails() {
            return linkDetails;
        }

    }

    private static final class SymbolicLinkDetailsImpl implements SymbolicLinkDetails {
        private String target;
        private final ZipArchiveEntry entry;
        private final ZipFile zip;
        private Boolean targetExists = null;
        private ZipArchiveEntry targetEntry = null;

        SymbolicLinkDetailsImpl(ZipArchiveEntry entry, ZipFile zip) {
            this.entry = entry;
            this.zip = zip;
        }

        @Override
        public boolean isRelative() {
            return targetExists();
        }

        @Override
        public String getTarget() {
            if (target == null) {
                target = getTarget(entry);
            }
            return target;
        }

        @Override
        public boolean targetExists() {
            if (targetExists == null) {
                targetEntry = getTargetEntry(entry);
                targetExists = targetEntry != null;
            }
            return targetExists;
        }

        public ZipArchiveEntry getTargetEntry() {
            return targetEntry;
        }

        private String getTarget(ZipArchiveEntry entry) {
            try {
                return zip.getUnixSymlink(entry);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Nullable
        private ZipArchiveEntry getTargetEntry(ZipArchiveEntry entry) {
            String path = entry.getName();
            ArrayList<String> parts = new ArrayList<>(Arrays.asList(path.split("/")));
            if (getTargetFollowingLinks(entry, parts, entry)) {
                String targetPath = String.join("/", parts);
                ZipArchiveEntry targetEntry = zip.getEntry(targetPath);
                if (targetEntry == null) { //retry for directories
                    targetEntry = zip.getEntry(targetPath + "/");
                }
                return targetEntry;
            } else {
                return null;
            }
        }

        private boolean getTargetFollowingLinks(ZipArchiveEntry entry, ArrayList<String> parts, ZipArchiveEntry originalEntry) {
            parts.remove(parts.size() - 1);
            String target = getTarget(entry);
            for (String targetPart : target.split("/")) {
                if (targetPart.equals("..")) {
                    if (parts.isEmpty()) {
                        return false;
                    }
                    parts.remove(parts.size() - 1);
                } else if (targetPart.equals(".")) {
                    continue;
                } else {
                    parts.add(targetPart);
                    String currentPath = String.join("/", parts);
                    ZipArchiveEntry currentEntry = zip.getEntry(currentPath);
                    if (currentEntry != null && currentEntry.isUnixSymlink()) {
                        if (currentEntry == originalEntry) {
                            return false; //cycle
                        }
                        boolean success = getTargetFollowingLinks(currentEntry, parts, originalEntry);
                        if (!success) {
                            return false;
                        }
                    }
                }
            }
            return true;
        }
    }
}
