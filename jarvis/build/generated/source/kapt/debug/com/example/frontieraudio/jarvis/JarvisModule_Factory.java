package com.example.frontieraudio.jarvis;

import com.example.frontieraudio.core.FrontierCore;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata
@DaggerGenerated
@Generated(
    value = "dagger.internal.codegen.ComponentProcessor",
    comments = "https://dagger.dev"
)
@SuppressWarnings({
    "unchecked",
    "rawtypes",
    "KotlinInternal",
    "KotlinInternalInJava",
    "cast",
    "deprecation"
})
public final class JarvisModule_Factory implements Factory<JarvisModule> {
  private final Provider<FrontierCore> frontierCoreProvider;

  public JarvisModule_Factory(Provider<FrontierCore> frontierCoreProvider) {
    this.frontierCoreProvider = frontierCoreProvider;
  }

  @Override
  public JarvisModule get() {
    return newInstance(frontierCoreProvider.get());
  }

  public static JarvisModule_Factory create(Provider<FrontierCore> frontierCoreProvider) {
    return new JarvisModule_Factory(frontierCoreProvider);
  }

  public static JarvisModule newInstance(FrontierCore frontierCore) {
    return new JarvisModule(frontierCore);
  }
}
