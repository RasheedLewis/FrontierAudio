package com.example.frontieraudio.core.permissions;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.util.Log;
import androidx.core.content.ContextCompat;
import dagger.hilt.android.qualifiers.ApplicationContext;
import javax.inject.Inject;
import javax.inject.Singleton;

@javax.inject.Singleton()
@kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u00008\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0000\n\u0002\u0018\u0002\n\u0002\b\u0002\n\u0002\u0010\u0002\n\u0000\n\u0002\u0010$\n\u0002\u0010\u000e\n\u0002\u0010\u000b\n\u0002\b\u0003\n\u0002\u0010 \n\u0002\b\u0002\n\u0002\u0018\u0002\n\u0002\b\u0002\b\u0007\u0018\u0000 \u00122\u00020\u0001:\u0001\u0012B\u0011\b\u0007\u0012\b\b\u0001\u0010\u0002\u001a\u00020\u0003\u00a2\u0006\u0002\u0010\u0004J\u001a\u0010\u0005\u001a\u00020\u00062\u0012\u0010\u0007\u001a\u000e\u0012\u0004\u0012\u00020\t\u0012\u0004\u0012\u00020\n0\bJ\u0010\u0010\u000b\u001a\u00020\n2\u0006\u0010\f\u001a\u00020\tH\u0002J\f\u0010\r\u001a\b\u0012\u0004\u0012\u00020\t0\u000eJ\u0006\u0010\u000f\u001a\u00020\nJ\u0006\u0010\u0010\u001a\u00020\u0011R\u000e\u0010\u0002\u001a\u00020\u0003X\u0082\u0004\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0013"}, d2 = {"Lcom/example/frontieraudio/core/permissions/PermissionManager;", "", "appContext", "Landroid/content/Context;", "(Landroid/content/Context;)V", "handlePermissionResult", "", "result", "", "", "", "isGranted", "permission", "missingCriticalPermissions", "", "shouldRequestCriticalPermissions", "snapshot", "Lcom/example/frontieraudio/core/permissions/PermissionSnapshot;", "Companion", "core_debug"})
public final class PermissionManager {
    @org.jetbrains.annotations.NotNull()
    private final android.content.Context appContext = null;
    @org.jetbrains.annotations.NotNull()
    private static final java.lang.String TAG = "PermissionManager";
    @org.jetbrains.annotations.NotNull()
    private static final com.example.frontieraudio.core.permissions.PermissionManager.Companion Companion = null;
    
    @javax.inject.Inject()
    public PermissionManager(@dagger.hilt.android.qualifiers.ApplicationContext()
    @org.jetbrains.annotations.NotNull()
    android.content.Context appContext) {
        super();
    }
    
    @org.jetbrains.annotations.NotNull()
    public final com.example.frontieraudio.core.permissions.PermissionSnapshot snapshot() {
        return null;
    }
    
    @org.jetbrains.annotations.NotNull()
    public final java.util.List<java.lang.String> missingCriticalPermissions() {
        return null;
    }
    
    public final boolean shouldRequestCriticalPermissions() {
        return false;
    }
    
    public final void handlePermissionResult(@org.jetbrains.annotations.NotNull()
    java.util.Map<java.lang.String, java.lang.Boolean> result) {
    }
    
    private final boolean isGranted(java.lang.String permission) {
        return false;
    }
    
    @kotlin.Metadata(mv = {1, 9, 0}, k = 1, xi = 48, d1 = {"\u0000\u0012\n\u0002\u0018\u0002\n\u0002\u0010\u0000\n\u0002\b\u0002\n\u0002\u0010\u000e\n\u0000\b\u0082\u0003\u0018\u00002\u00020\u0001B\u0007\b\u0002\u00a2\u0006\u0002\u0010\u0002R\u000e\u0010\u0003\u001a\u00020\u0004X\u0082T\u00a2\u0006\u0002\n\u0000\u00a8\u0006\u0005"}, d2 = {"Lcom/example/frontieraudio/core/permissions/PermissionManager$Companion;", "", "()V", "TAG", "", "core_debug"})
    static final class Companion {
        
        private Companion() {
            super();
        }
    }
}