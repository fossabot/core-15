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
package jdk.internal.loader;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.lang.invoke.MethodHandle;


public class ClassLoaderDelegate extends BuiltinClassLoader {
	private ClassLoader classLoader;
	private MethodHandle loadClassMethod;
	
	ClassLoaderDelegate(String name, BuiltinClassLoader parent, URLClassPath ucp) {
		super(name, parent, ucp);
	}
	
	public void init(ClassLoader classLoader, MethodHandle loadClassMethodHandle) {
		this.classLoader = classLoader;
		this.loadClassMethod = loadClassMethodHandle;
	}
	
	@Override
	protected Class<?> loadClassOrNull(String className, boolean resolve) {
		try {
			return (Class<?>)loadClassMethod.invoke(classLoader, className, resolve);
		} catch (Throwable exc) {
			System.out.println("Class " + className + " not found");
			return null;
		}
	}
	
	@Override
	protected Class<?> loadClass(String className, boolean resolve) throws ClassNotFoundException {
		try {
			return (Class<?>)loadClassMethod.invoke(classLoader, className, resolve);
		} catch (Throwable exc) {
			System.out.println("Class " + className + " not found");
			return null;
		}
	}
	
	@Override
	public URL getResource(String name) {
		return classLoader.getResource(name);
	}
	
	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		return classLoader.getResources(name);
	}
	
    @Override
    public InputStream getResourceAsStream(String name) {
    	return classLoader.getResourceAsStream(name);
    }
}
