package com.example.frontieraudio.transcriber;

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
public final class TranscriberModule_Factory implements Factory<TranscriberModule> {
  private final Provider<FrontierCore> frontierCoreProvider;

  public TranscriberModule_Factory(Provider<FrontierCore> frontierCoreProvider) {
    this.frontierCoreProvider = frontierCoreProvider;
  }

  @Override
  public TranscriberModule get() {
    return newInstance(frontierCoreProvider.get());
  }

  public static TranscriberModule_Factory create(Provider<FrontierCore> frontierCoreProvider) {
    return new TranscriberModule_Factory(frontierCoreProvider);
  }

  public static TranscriberModule newInstance(FrontierCore frontierCore) {
    return new TranscriberModule(frontierCore);
  }
}
