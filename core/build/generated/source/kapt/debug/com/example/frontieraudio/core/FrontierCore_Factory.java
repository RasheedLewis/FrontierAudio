package com.example.frontieraudio.core;

import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;

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
public final class FrontierCore_Factory implements Factory<FrontierCore> {
  @Override
  public FrontierCore get() {
    return newInstance();
  }

  public static FrontierCore_Factory create() {
    return InstanceHolder.INSTANCE;
  }

  public static FrontierCore newInstance() {
    return new FrontierCore();
  }

  private static final class InstanceHolder {
    private static final FrontierCore_Factory INSTANCE = new FrontierCore_Factory();
  }
}
