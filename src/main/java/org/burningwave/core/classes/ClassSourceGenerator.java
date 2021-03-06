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

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ClassSourceGenerator extends SourceGenerator.Abst {
	private Collection<AnnotationSourceGenerator> annotations;
	private Collection<String> outerCode;
	private Integer modifier;
	private String classType;
	private TypeDeclarationSourceGenerator typeDeclaration;
	private String expands;
	private TypeDeclarationSourceGenerator expandedType;
	private String concretize;
	private Collection<TypeDeclarationSourceGenerator> concretizedTypes;
	private Collection<VariableSourceGenerator> fields;
	private Collection<FunctionSourceGenerator> constructors;
	private Collection<FunctionSourceGenerator> methods;
	private Collection<ClassSourceGenerator> innerClasses;
	
	private ClassSourceGenerator(String classType, TypeDeclarationSourceGenerator typeDeclaration) {
		this.classType = classType;
		this.typeDeclaration = typeDeclaration;
	}
	
	TypeDeclarationSourceGenerator getTypeDeclaration() {
		return typeDeclaration;
	}
	
	public static ClassSourceGenerator create(TypeDeclarationSourceGenerator type) {
		return new ClassSourceGenerator("class", type);
	}
	
	public static ClassSourceGenerator createInterface(TypeDeclarationSourceGenerator type) {
		return new ClassSourceGenerator("interface", type);
	}
	
	public static ClassSourceGenerator createAnnotation(TypeDeclarationSourceGenerator type) {
		return new ClassSourceGenerator("@interface", type);
	}
	
	public static ClassSourceGenerator createEnum(TypeDeclarationSourceGenerator type) {
		return new ClassSourceGenerator("enum", type);
	}
	
	public ClassSourceGenerator addModifier(Integer modifier) {
		if (this.modifier == null) {
			this.modifier = modifier;
		} else {
			this.modifier |= modifier; 
		}
		return this;
	}
	
	Integer getModifier() {
		return this.modifier;
	}
	
	String getSimpleName() {
		return typeDeclaration.getSimpleName();
	}
	
	public ClassSourceGenerator expands(Class<?> extendedClass) {
		return expands(TypeDeclarationSourceGenerator.create(extendedClass));		
	}
	
	public ClassSourceGenerator expands(TypeDeclarationSourceGenerator expandedType) {
		if (classType.equals("interface")) {
			this.concretize = "extends";
			return addConcretizedType(expandedType);
		} else {
			expands = "extends";
			this.expandedType = expandedType;
			return this;
		}
	}
	
	public ClassSourceGenerator addConcretizedType(Class<?>... concretizedTypes) {
		if (classType.equals("interface")) {
			concretize = "extends";
		} else {
			concretize = "implements";
		}
		this.concretizedTypes = Optional.ofNullable(this.concretizedTypes).orElseGet(ArrayList::new);
		for (Class<?> cls : concretizedTypes) {
			if (!isAlreadyAdded(cls.getName())) {
				this.concretizedTypes.add(TypeDeclarationSourceGenerator.create(cls));
			}
		}
		return this;	
	}
	
	public ClassSourceGenerator addConcretizedType(TypeDeclarationSourceGenerator... concretizedTypes) {
		if (classType.equals("interface")) {
			concretize = "extends";
		} else {
			concretize = "implements";
		}
		this.concretizedTypes = Optional.ofNullable(this.concretizedTypes).orElseGet(ArrayList::new);
		for (TypeDeclarationSourceGenerator typeDeclarationSG : concretizedTypes) {
			if (!isAlreadyAdded(typeDeclarationSG.getName())) {
				this.concretizedTypes.add(typeDeclarationSG);
			}
		}
		return this;		
	}
	
	private boolean isAlreadyAdded(String className) {
		boolean isAlreadyAdded = false;
		for (TypeDeclarationSourceGenerator typeDeclarationSG : this.concretizedTypes) {
			if ((typeDeclarationSG.getName() != null && typeDeclarationSG.getName().equals(className))) {
				isAlreadyAdded = true;
				break;
			}
		}
		return isAlreadyAdded;
	}
	
	public ClassSourceGenerator addOuterCodeRow(String... codes) {
		this.outerCode = Optional.ofNullable(this.outerCode).orElseGet(ArrayList::new);
		for (String code : codes) {
			if (!this.outerCode.isEmpty()) {
				this.outerCode.add("\n" + code);
			} else {
				this.outerCode.add(code);
			}
		}
		return this;
	}
	
	public ClassSourceGenerator addAnnotation(AnnotationSourceGenerator... annotations) {
		this.annotations = Optional.ofNullable(this.annotations).orElseGet(ArrayList::new);
		for (AnnotationSourceGenerator annotation : annotations) {
			this.annotations.add(annotation);
		}
		return this;
	}
	
	public ClassSourceGenerator addField(VariableSourceGenerator... fields) {
		this.fields = Optional.ofNullable(this.fields).orElseGet(ArrayList::new);
		for (VariableSourceGenerator field : fields) {
			this.fields.add(field);
			if (classType.equals("enum")) {
				field.setAssignementOperator(null);
				field.setDelimiter(COMMA);
			}
		}
		return this;
	}
	
	public ClassSourceGenerator addConstructor(FunctionSourceGenerator... constructors) {
		this.constructors = Optional.ofNullable(this.constructors).orElseGet(ArrayList::new);
		for (FunctionSourceGenerator constructor : constructors) {
			this.constructors.add(constructor);
			constructor.setName(this.typeDeclaration.getSimpleName());
			constructor.setReturnType((TypeDeclarationSourceGenerator)null);
		}
		return this;
	}
	
	public ClassSourceGenerator addMethod(FunctionSourceGenerator... methods) {
		this.methods = Optional.ofNullable(this.methods).orElseGet(ArrayList::new);
		for (FunctionSourceGenerator method : methods) {
			this.methods.add(method);
		}
		return this;
	}
	
	public ClassSourceGenerator addInnerClass(ClassSourceGenerator... classes) {
		this.innerClasses = Optional.ofNullable(this.innerClasses).orElseGet(ArrayList::new);
		for (ClassSourceGenerator cls : classes) {
			this.innerClasses.add(cls);
		}
		return this;
	}
	
	private String getAnnotations() {
		return Optional.ofNullable(annotations).map(annts -> getOrEmpty(annts, "\n") +"\n").orElseGet(() -> null);
	}
	
	private String getFieldsCode() {
		return Optional.ofNullable(fields).map(flds -> "\t" + getOrEmpty(flds, "\n").replace("\n", "\n\t")).orElseGet(() -> null);
	}
	
	private String getFunctionCode(Collection<FunctionSourceGenerator> functions) {
		return Optional.ofNullable(functions).map(mths -> "\t" + getOrEmpty(mths, "\n\n").replace("\n", "\n\t")).orElseGet(() -> null);
	}
	
	String getInnerClassesCode() {
		String innerClassesAsString = null;
		if (innerClasses != null) {
			innerClassesAsString = "\t";
			for (ClassSourceGenerator cls : innerClasses) {
				innerClassesAsString += (cls.make()).replaceAll("\n(.)", "\n\t$1");
			}
		}
		return innerClassesAsString;
	}
	
	Map<String, ClassSourceGenerator> getAllInnerClasses() {
		Map<String, ClassSourceGenerator> classes = new HashMap<>();
		Optional.ofNullable(innerClasses).ifPresent(innerclasses -> {
			innerclasses.forEach(innerClass -> {
				classes.put(typeDeclaration.getSimpleName() + "$" + innerClass.typeDeclaration.getSimpleName(), innerClass);
				for (Map.Entry<String, ClassSourceGenerator> cls : innerClass.getAllInnerClasses().entrySet()) {
					classes.put(typeDeclaration.getSimpleName() + "$" + cls.getKey(), cls.getValue());
				}
			});
		});
		return classes;
	}
	
	@Override
	public String make() {
		String annotations = getAnnotations();
		String fieldsCode = getFieldsCode();
		String constructorsCode = getFunctionCode(constructors);
		String methodsCode = getFunctionCode(methods);
		String innerClassesCode = getInnerClassesCode();
		return
			getOrEmpty(
				getOuterCode(),
				annotations,
				Optional.ofNullable(modifier).map(mod -> Modifier.toString(this.modifier)).orElseGet(() -> null),
				classType,
				typeDeclaration,
				expands,
				expandedType,
				concretize,
				getOrEmpty(concretizedTypes, ", "), 
				"{",
				fieldsCode != null? "\n\n" + fieldsCode : null,
				constructorsCode != null? "\n\n" + constructorsCode : null,
				methodsCode != null? "\n\n" + methodsCode : null,
				innerClassesCode != null? "\n\n" + innerClassesCode : null,
				"\n\n}"
			);
	}

	protected String getOuterCode() {
		return Optional.ofNullable(outerCode).map(outerCode ->
			getOrEmpty(outerCode) +"\n"
		).orElseGet(() -> null);
	}
	
	Collection<TypeDeclarationSourceGenerator> getTypeDeclarations() {
		Collection<TypeDeclarationSourceGenerator> types = typeDeclaration.getTypeDeclarations();
		Optional.ofNullable(annotations).ifPresent(annotations -> {
			for (AnnotationSourceGenerator annotation : annotations) {
				types.addAll(annotation.getTypeDeclarations());
			}
		});	
		Optional.ofNullable(expandedType).ifPresent(expandedType -> {
			types.addAll(expandedType.getTypeDeclarations());
		});
		Optional.ofNullable(concretizedTypes).ifPresent(concretizedTypes -> {
			types.addAll(concretizedTypes);
			for (TypeDeclarationSourceGenerator type : concretizedTypes) {
				types.addAll(type.getTypeDeclarations());
			}
		});
		Optional.ofNullable(fields).ifPresent(fields -> {
			for (VariableSourceGenerator field : fields) {
				types.addAll(field.getTypeDeclarations());
			}
		});
		Optional.ofNullable(constructors).ifPresent(constructors -> {
			for (FunctionSourceGenerator constructor : constructors) {
				types.addAll(constructor.getTypeDeclarations());
			}
		});
		Optional.ofNullable(methods).ifPresent(methods -> {
			for (FunctionSourceGenerator method : methods) {
				types.addAll(method.getTypeDeclarations());
			}
		});
		Optional.ofNullable(innerClasses).ifPresent(innerClasses -> {
			for (ClassSourceGenerator cls : innerClasses) {
				types.addAll(cls.getTypeDeclarations());
			}
		});	
		return types;
	}
}
