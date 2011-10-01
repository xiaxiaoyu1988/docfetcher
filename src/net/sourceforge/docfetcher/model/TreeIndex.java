/*******************************************************************************
 * Copyright (c) 2010, 2011 Tran Nam Quang.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    Tran Nam Quang - initial API and implementation
 *******************************************************************************/

package net.sourceforge.docfetcher.model;

import java.io.File;
import java.io.IOException;
import java.util.List;

import net.sourceforge.docfetcher.model.index.IndexingConfig;
import net.sourceforge.docfetcher.util.Util;
import net.sourceforge.docfetcher.util.annotations.ImmutableCopy;
import net.sourceforge.docfetcher.util.annotations.NotNull;
import net.sourceforge.docfetcher.util.annotations.Nullable;
import net.sourceforge.docfetcher.util.annotations.VisibleForPackageGroup;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.RAMDirectory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.io.Files;

@VisibleForPackageGroup
@SuppressWarnings("serial")
public abstract class TreeIndex <
	D extends Document<D, F>,
	F extends Folder<D, F>> implements LuceneIndex {
	
	public enum IndexingResult {
		SUCCESS_CHANGED,
		SUCCESS_UNCHANGED,
		FAILURE,
	}
	
	private final IndexingConfig config;
	@NotNull private F rootFolder;
	@Nullable private final File fileIndexDir;
	@Nullable private transient RAMDirectory ramIndexDir;
	
	// if indexDir is null, all content is written to a RAM index, which
	// can be retrieved via getLuceneDir
	protected TreeIndex(@Nullable File indexParentDir,
	                    @NotNull File rootFile) {
		Util.checkNotNull(rootFile);
		
		this.config = new IndexingConfig(rootFile) {
			@Override
			protected void onRootFileChanged() {
				updateRootFolder();
			}
		};
		updateRootFolder();
		
		if (indexParentDir == null) {
			fileIndexDir = null;
			ramIndexDir = new RAMDirectory();
		}
		else {
			long id = Util.getTimestamp();
			String indexDirName = getIndexDirName(rootFile) + "_" + id;
			fileIndexDir = new File(indexParentDir, indexDirName);
		}
	}
	
	private void updateRootFolder() {
		File rootFile = config.getRootFile();
		rootFolder = createRootFolder(rootFile.getName(), rootFile.getPath());
		Util.checkNotNull(rootFolder);
	}
	
	@NotNull
	protected abstract String getIndexDirName(@NotNull File rootFile);
	
	@NotNull
	protected abstract F createRootFolder(	@NotNull String name,
											@NotNull String path);

	@NotNull
	public final IndexingConfig getConfig() {
		return config;
	}
	
	@NotNull
	public final File getRootFile() {
		return config.getRootFile();
	}
	
	@Nullable
	public final File getIndexDir() {
		return fileIndexDir;
	}
	
	@NotNull
	@VisibleForTesting
	public final Directory getLuceneDir() throws IOException {
		if (fileIndexDir != null) {
			assert ramIndexDir == null;
			return FSDirectory.open(fileIndexDir);
		}
		if (ramIndexDir == null) // may be null after deserialization
			ramIndexDir = new RAMDirectory();
		return ramIndexDir;
	}
	
	@NotNull
	public final F getRootFolder() {
		return rootFolder;
	}
	
	@NotNull
	public final String getDisplayName() {
		return rootFolder.getDisplayName();
	}
	
	@NotNull
	public final Iterable<ViewNode> getChildren() {
		return rootFolder.getChildren();
	}
	
	public final void clear() {
		clear(false);
	}
	
	public final void delete() {
		clear(true);
	}
	
	private void clear(boolean removeTopLevel) {
		if (fileIndexDir != null) {
			if (fileIndexDir.exists()) {
				try {
					if (removeTopLevel)
						Files.deleteRecursively(fileIndexDir);
					else
						Files.deleteDirectoryContents(fileIndexDir);
				}
				catch (IOException e) {
					Util.printErr(e);
				}
			}
		}
		else {
			assert ramIndexDir != null;
			ramIndexDir = new RAMDirectory();
		}
		
		/*
		 * The last-modified field of the root folder must be cleared so that
		 * the next index update will detect the root folder as modified.
		 */
		rootFolder.setLastModified(null);
		rootFolder.removeChildren();
	}
	
	public final boolean isChecked() {
		return rootFolder.isChecked();
	}
	
	public final void setChecked(boolean isChecked) {
		rootFolder.setChecked(isChecked);
	}
	
	@NotNull
	public final TreeCheckState getTreeCheckState() {
		return rootFolder.getTreeCheckState();
	}
	
	public final boolean isIndex() {
		return true;
	}
	
	@ImmutableCopy
	@NotNull
	public final List<String> getDocumentIds() {
		return rootFolder.getDocumentIds();
	}
	
	public final boolean isWatchFolders() {
		return config.isWatchFolders();
	}
	
	public final void setWatchFolders(boolean watchFolders) {
		if (config.isWatchFolders() == watchFolders)
			return;
		config.setWatchFolders(watchFolders);
		LuceneIndex.evtWatchFoldersChanged.fire(this);
	}
	
}
