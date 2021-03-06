/*
 * This file is part of Burningwave Core.
 *
 * Author: Roberto Gentili
 *
 * Hosted at: https://github.com/burningwave/core
 *
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2019 Roberto Gentili
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.Classes;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.burningwave.core.concurrent.ParallelTasksManager;
import org.burningwave.core.io.ClassFileScanConfig;
import org.burningwave.core.io.FileSystemItem;
import org.burningwave.core.io.FileSystemScanner;
import org.burningwave.core.io.FileSystemScanner.Scan;
import org.burningwave.core.io.IterableZipContainer;
import org.burningwave.core.io.PathHelper;

public class ClassPathHunter extends ClassPathScannerWithCachingSupport<Collection<Class<?>>, ClassPathHunter.SearchContext, ClassPathHunter.SearchResult> {
	private ClassPathHunter(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemScanner fileSystemScanner,
		PathHelper pathHelper
	) {
		super(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemScanner,
			pathHelper,
			(initContext) -> SearchContext._create(initContext),
			(context) -> new ClassPathHunter.SearchResult(context)
		);
	}
	
	public static ClassPathHunter create(
		Supplier<ByteCodeHunter> byteCodeHunterSupplier,
		Supplier<ClassHunter> classHunterSupplier,
		FileSystemScanner fileSystemScanner,
		PathHelper pathHelper
	) {
		return new ClassPathHunter(
			byteCodeHunterSupplier,
			classHunterSupplier,
			fileSystemScanner,
			pathHelper
		);
	}
	
	@Override
	<S extends SearchConfigAbst<S>> ClassCriteria.TestContext testCachedItem(SearchContext context, String baseAbsolutePath, String currentScannedItemAbsolutePath, Collection<Class<?>> classes) {
		ClassCriteria.TestContext testContext = context.testCriteria(null);
		for (Class<?> cls : classes) {
			if ((testContext = context.testCriteria(context.retrieveClass(cls))).getResult()) {
				break;
			}
		}		
		return testContext;
	}
	
	@Override
	void retrieveItemFromFileInputStream(
		SearchContext context, 
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext scanItemContext,
		JavaClass javaClass
	) {	
		String classPath = scanItemContext.getScannedItem().getAbsolutePath();
		classPath = classPath.substring(
			0, classPath.lastIndexOf(
				javaClass.getName().replace(".", "/"), classPath.length()
			)
		);	
		context.addItemFound(
			scanItemContext.getBasePathAsString(),
			classPath,
			context.loadClass(javaClass.getName())
		);
	}

	@Override
	void retrieveItemFromZipEntry(
		SearchContext context,
		ClassCriteria.TestContext criteriaTestContext,
		Scan.ItemContext scanItemContext,
		JavaClass javaClass
	) {
		String fsObject = null;
		IterableZipContainer.Entry zipEntry = scanItemContext.getScannedItem().getWrappedItem();
		if (zipEntry.getName().equals(javaClass.getPath())) {
			fsObject = zipEntry.getParentContainer().getAbsolutePath();
		} else {
			String zipEntryAbsolutePath = zipEntry.getAbsolutePath();
			zipEntryAbsolutePath = zipEntryAbsolutePath.substring(0, zipEntryAbsolutePath.lastIndexOf(javaClass.getName().replace(".", "/")));
			fsObject = zipEntryAbsolutePath;
		}
		context.addItemFound(scanItemContext.getBasePathAsString(), fsObject, context.loadClass(javaClass.getName()));
	}
	
	
	@Override
	public void close() {
		super.close();
	}
	
	public static class SearchContext extends org.burningwave.core.classes.SearchContext<Collection<Class<?>>> {
		ParallelTasksManager tasksManager;
		
		SearchContext(InitContext initContext) {
			super(initContext);
			ClassFileScanConfig scanConfig = initContext.getClassFileScanConfiguration();
			this.tasksManager = ParallelTasksManager.create(scanConfig.getMaxParallelTasksForUnit());
		}		

		static SearchContext _create(InitContext initContext) {
			return new SearchContext(initContext);
		}

		
		void addItemFound(String basePathAsString, String classPathAsFile, Class<?> testedClass) {
			Map<String, Collection<Class<?>>> testedClassesForClassPathMap = retrieveCollectionForPath(
				itemsFoundMap,
				HashMap::new,
				basePathAsString
			);
			Collection<Class<?>> testedClassesForClassPath = testedClassesForClassPathMap.get(classPathAsFile);
			if (testedClassesForClassPath == null) {
				synchronized (Classes.getId(testedClassesForClassPathMap, classPathAsFile)) {
					testedClassesForClassPath = testedClassesForClassPathMap.get(classPathAsFile);
					if (testedClassesForClassPath == null) {
						testedClassesForClassPathMap.put(classPathAsFile, testedClassesForClassPath = ConcurrentHashMap.newKeySet());
					}
				}
			}
			testedClassesForClassPath.add(testedClass);
			itemsFoundFlatMap.putAll(testedClassesForClassPathMap);
		}
		
		@Override
		public void close() {
			tasksManager.close();
			super.close();
		}
	}
	
	public static class SearchResult extends org.burningwave.core.classes.SearchResult<Collection<Class<?>>> {
		Collection<FileSystemItem> classPaths;
		
		public SearchResult(SearchContext context) {
			super(context);
		}
		
		public Collection<FileSystemItem> getClassPaths() {
			if (classPaths == null) {
				Map<String, Collection<Class<?>>> itemsFoundFlatMaps = context.getItemsFoundFlatMap();
				synchronized (itemsFoundFlatMaps) {
					if (classPaths == null) {
						classPaths = itemsFoundFlatMaps.keySet().stream().map(path -> 
							FileSystemItem.ofPath(path)
						).collect(
							Collectors.toCollection(
								HashSet::new
							)
						);
					}
				}
			}
			return classPaths;
		}
	}
}
