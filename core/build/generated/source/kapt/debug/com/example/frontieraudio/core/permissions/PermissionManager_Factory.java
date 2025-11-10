package com.example.frontieraudio.core.permissions;

import android.content.Context;
import dagger.internal.DaggerGenerated;
import dagger.internal.Factory;
import dagger.internal.QualifierMetadata;
import dagger.internal.ScopeMetadata;
import javax.annotation.processing.Generated;
import javax.inject.Provider;

@ScopeMetadata("javax.inject.Singleton")
@QualifierMetadata("dagger.hilt.android.qualifiers.ApplicationContext")
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
public final class PermissionManager_Factory implements Factory<PermissionManager> {
  private final Provider<Context> appContextProvider;

  public PermissionManager_Factory(Provider<Context> appContextProvider) {
    this.appContextProvider = appContextProvider;
  }

  @Override
  public PermissionManager get() {
    return newInstance(appContextProvider.get());
  }

  public static PermissionManager_Factory create(Provider<Context> appContextProvider) {
    return new PermissionManager_Factory(appContextProvider);
  }

  public static PermissionManager newInstance(Context appContext) {
    return new PermissionManager(appContext);
  }
}
