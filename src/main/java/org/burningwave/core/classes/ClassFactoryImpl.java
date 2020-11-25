package org.burningwave.core.classes;

import static org.burningwave.core.assembler.StaticComponentContainer.BackgroundExecutor;
import static org.burningwave.core.assembler.StaticComponentContainer.IterableObjectHelper;
import static org.burningwave.core.assembler.StaticComponentContainer.SourceCodeHandler;
import static org.burningwave.core.assembler.StaticComponentContainer.Synchronizer;
import static org.burningwave.core.assembler.StaticComponentContainer.Throwables;

import java.util.Collection;
import java.util.Iterator;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

import org.burningwave.core.Component;
import org.burningwave.core.function.MultiParamsFunction;
import org.burningwave.core.io.PathHelper;
import org.burningwave.core.iterable.Properties;
import org.burningwave.core.iterable.Properties.Event;

@SuppressWarnings("unchecked")
public class ClassFactoryImpl implements ClassFactory, Component {
	PathHelper pathHelper;
	ClassPathHelper classPathHelper;
	JavaMemoryCompiler javaMemoryCompiler;
	private PojoSubTypeRetriever pojoSubTypeRetriever;	
	ByteCodeHunter byteCodeHunter;
	private ClassPathHunter classPathHunter;
	private Supplier<ClassPathHunter> classPathHunterSupplier;
	private ClassLoaderManager<ClassLoader> defaultClassLoaderManager;
	private Collection<ClassRetriever> classRetrievers;
	Properties config;
	
	ClassFactoryImpl(
		ByteCodeHunter byteCodeHunter,
		Supplier<ClassPathHunter> classPathHunterSupplier,
		JavaMemoryCompiler javaMemoryCompiler,
		PathHelper pathHelper,
		ClassPathHelper classPathHelper,
		Object defaultClassLoaderOrDefaultClassLoaderSupplier,
		Properties config
	) {	
		this.byteCodeHunter = byteCodeHunter;
		this.classPathHunterSupplier = classPathHunterSupplier;
		this.javaMemoryCompiler = javaMemoryCompiler;
		this.pathHelper = pathHelper;
		this.classPathHelper = classPathHelper;
		this.pojoSubTypeRetriever = PojoSubTypeRetriever.createDefault(this);
		this.defaultClassLoaderManager = new ClassLoaderManager<>(
			defaultClassLoaderOrDefaultClassLoaderSupplier
		);
		this.classRetrievers = new CopyOnWriteArrayList<>();
		this.config = config;
		listenTo(config);
	}
	
	@Override
	public <K, V> void processChangeNotification(Properties properties, Event event, K key, V newValue,
			V previousValue) {
		if (event.name().equals(Event.PUT.name())) {
			if (key instanceof String) {
				String keyAsString = (String)key;
				if (keyAsString.equals(Configuration.Key.DEFAULT_CLASS_LOADER)) {
					this.defaultClassLoaderManager.reset();
				}
			}
		}
	}
	
	ClassLoader getDefaultClassLoader(Object client) {
		return this.defaultClassLoaderManager.get(client);
	}
	
	ClassPathHunter getClassPathHunter() {
		return classPathHunter != null? classPathHunter :
			(classPathHunter = classPathHunterSupplier.get());
	}
	
	@Override
	public ClassRetriever loadOrBuildAndDefine(UnitSourceGenerator... unitsCode) {
		return loadOrBuildAndDefine(LoadOrBuildAndDefineConfig.forUnitSourceGenerator(unitsCode));
	}
	
	@Override
	public <L extends LoadOrBuildAndDefineConfigAbst<L>> ClassRetriever loadOrBuildAndDefine(L config) {
		if (config.isVirtualizeClassesEnabled()) {
			config.addClassPaths(pathHelper.getBurningwaveRuntimeClassPath());
		}
		return loadOrBuildAndDefine(
			config.getClassesName(),
			config.getCompileConfigSupplier(),			
			config.isUseOneShotJavaCompilerEnabled(),
			IterableObjectHelper.merge(
				() -> config.getClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(),
				() -> config.getAdditionalClassRepositoriesWhereToSearchNotFoundClassesDuringLoading(),
				() -> {
					Collection<String> classRepositoriesForNotFoundClasses = pathHelper.getPaths(
						Configuration.Key.CLASS_REPOSITORIES_FOR_DEFAULT_CLASS_LOADER
					);
					if (!classRepositoriesForNotFoundClasses.isEmpty()) {
						config.addClassRepositoriesWhereToSearchNotFoundClasses(classRepositoriesForNotFoundClasses);
					}
					return classRepositoriesForNotFoundClasses;
				}
			),
			(client) -> Optional.ofNullable(
				config.getClassLoader()
			).orElseGet(() -> 
				getDefaultClassLoader(client)
			)
		);
	}
	
	private ClassRetriever loadOrBuildAndDefine(
		Collection<String> classNames,
		Supplier<CompilationConfig> compileConfigSupplier,		
		boolean useOneShotJavaCompiler,
		Collection<String> additionalClassRepositoriesForClassLoader,
		Function<Object, ClassLoader> classLoaderSupplier
	) {
		try {
			Object temporaryClient = new Object(){};
			ClassLoader classLoader = classLoaderSupplier.apply(temporaryClient);			
			return new ClassRetriever(
				this,
				 (classRetriever) -> {
					if (classLoader instanceof MemoryClassLoader) {
						((MemoryClassLoader)classLoader).register(classRetriever);
						((MemoryClassLoader)classLoader).unregister(temporaryClient, true);
						if (classLoader != this.defaultClassLoaderManager.get()) {
							((MemoryClassLoader) classLoader).unregister(this, true);
						}
					}
					return classLoader;
				},
				compileConfigSupplier,
				useOneShotJavaCompiler,
				additionalClassRepositoriesForClassLoader,
				classNames
			);
		} catch (Throwable exc) {
			return Throwables.throwException(exc);
		}
	}

	
	@Override
	public PojoSubTypeRetriever createPojoSubTypeRetriever(PojoSourceGenerator sourceGenerator) {
		return PojoSubTypeRetriever.create(this, sourceGenerator);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(String className, Class<?>... superClasses) {
		return loadOrBuildAndDefinePojoSubType(null, className, superClasses);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(String className, int options, Class<?>... superClasses) {
		return loadOrBuildAndDefinePojoSubType(null, className, options, superClasses);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(ClassLoader classLoader, String className, int options, Class<?>... superClasses) {
		return pojoSubTypeRetriever.loadOrBuildAndDefine(classLoader, className, options, superClasses);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefinePojoSubType(ClassLoader classLoader, String className, Class<?>... superClasses) {
		return pojoSubTypeRetriever.loadOrBuildAndDefine(classLoader, className, PojoSourceGenerator.ALL_OPTIONS_DISABLED, superClasses);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefineFunctionSubType(int parametersCount) {
		return loadOrBuildAndDefineFunctionSubType(null, parametersCount);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefineFunctionSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "FunctionFor", "Parameters", parametersLength,
			(className, paramsL) -> SourceCodeHandler.generateFunction(className, paramsL)
		);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(int parametersCount) {
		return loadOrBuildAndDefineConsumerSubType(null, parametersCount);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefineConsumerSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "ConsumerFor", "Parameters", parametersLength,
			(className, paramsL) -> SourceCodeHandler.generateConsumer(className, paramsL)
		);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(int parametersLength) {
		return loadOrBuildAndDefinePredicateSubType(null, parametersLength);
	}
	
	@Override
	public <T> Class<T> loadOrBuildAndDefinePredicateSubType(ClassLoader classLoader, int parametersLength) {
		return loadOrBuildAndDefineFunctionInterfaceSubType(
			classLoader, "PredicateFor", "Parameters", parametersLength,
			(className, paramsL) -> SourceCodeHandler.generatePredicate(className, paramsL)
		);
	}
	
	private <T> Class<T> loadOrBuildAndDefineFunctionInterfaceSubType(
		ClassLoader classLoader,
		String classNamePrefix, 
		String classNameSuffix,
		int parametersLength,
		BiFunction<String, Integer, UnitSourceGenerator> unitSourceGeneratorSupplier
	) {
		String functionalInterfaceName = classNamePrefix + parametersLength +	classNameSuffix;
		String packageName = MultiParamsFunction.class.getPackage().getName();
		String className = packageName + "." + functionalInterfaceName;
		ClassRetriever classRetriever = loadOrBuildAndDefine(
			LoadOrBuildAndDefineConfig.forUnitSourceGenerator(
				unitSourceGeneratorSupplier.apply(className, parametersLength)
			).useClassLoader(
				classLoader
			)
		);
		Class<T> cls = (Class<T>)classRetriever.get(className);
		classRetriever.close();
		return cls;
	}
	
	boolean register(ClassRetriever classRetriever) {
		classRetrievers.add(classRetriever);
		return true;
	}
	
	boolean unregister(ClassRetriever classRetriever) {
		classRetrievers.remove(classRetriever);
		return true;
	}
	
	@Override
	public void closeClassRetrievers() {
		Synchronizer.execute(getOperationId("closeClassRetrievers"), () -> {
			Collection<ClassRetriever> classRetrievers = this.classRetrievers;
			if (classRetrievers != null) {
				Iterator<ClassRetriever> classRetrieverIterator = classRetrievers.iterator();		
				while(classRetrieverIterator.hasNext()) {
					ClassRetriever classRetriever = classRetrieverIterator.next();
					classRetriever.close();
				}
			}
		});
	}
	
	@Override
	public void reset(boolean closeClassRetrievers) {
		if (closeClassRetrievers) {
			closeClassRetrievers();
		}
		this.defaultClassLoaderManager.reset();		
	}
	
	@Override
	public void close() {
		closeResources(() -> this.classRetrievers == null, () -> {
			this.defaultClassLoaderManager.close();
			unregister(config);
			closeClassRetrievers();
			BackgroundExecutor.createTask(() -> {
				this.classRetrievers = null;
			}).submit();
			pathHelper = null;
			javaMemoryCompiler = null;
			pojoSubTypeRetriever = null;	
			byteCodeHunter = null;
			classPathHunter = null;
			classPathHunterSupplier = null;	
			config = null;
		});
	}

	
}