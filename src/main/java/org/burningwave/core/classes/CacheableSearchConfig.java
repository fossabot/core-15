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

import java.util.Collection;
import java.util.function.Predicate;

public class CacheableSearchConfig extends SearchConfigAbst<CacheableSearchConfig> {
	private Predicate<String> refreshCachePredicate;
	
	public CacheableSearchConfig refreshCache() {
		refreshCachePredicate = (path) ->
			this.paths.contains(path);
		this.checkForAddedClasses = true;
		return this;
	}
	
	public CacheableSearchConfig refreshCacheFor(Predicate<String> refreshCacheFor) {
		this.refreshCachePredicate = refreshCacheFor;
		return this;
	}
	
	Predicate<String> getRefreshCachePredicate() {
		return refreshCachePredicate;
	}
	
	@SafeVarargs
	CacheableSearchConfig(Collection<String>... pathsColl) {
		super(pathsColl);
	}
	
	@Override
	public CacheableSearchConfig createCopy() {
		CacheableSearchConfig copy = super.createCopy();
		return copy;
	}
	
	public SearchConfig withoutUsingCache() {
		return super.copyTo(SearchConfig.withoutUsingCache());
	}
	
	@Override
	public void close() {
		super.close();
	}

	@Override
	CacheableSearchConfig newInstance() {
		return new CacheableSearchConfig(this.paths);
	}
	
	@Override
	public <S extends SearchConfigAbst<S>> S copyTo(S destConfig) {
		((CacheableSearchConfig)destConfig).refreshCachePredicate = this.refreshCachePredicate;
		return super.copyTo(destConfig);
	}
}