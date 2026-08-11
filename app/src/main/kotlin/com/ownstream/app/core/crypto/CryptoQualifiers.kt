package com.ownstream.app.core.crypto

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class StorageKeyAlias

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IdentityKeyAlias
