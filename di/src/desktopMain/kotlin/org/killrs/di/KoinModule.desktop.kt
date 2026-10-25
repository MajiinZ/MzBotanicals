package org.killrs.di

import org.koin.dsl.module
import org.mz.data.DesktopMockOrderStorage
import org.mz.data.MongoMockOrderStorage
import org.mz.data.MockOrderStorage
import org.mz.killrs.manage_product.PhotoPicker

actual val targetModule = module {
    single<PhotoPicker> { PhotoPicker() }
    single<MockOrderStorage> { MongoMockOrderStorage(DesktopMockOrderStorage()) }
}
