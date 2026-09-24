package org.killrs.di

import org.koin.dsl.module
import org.mz.killrs.manage_product.PhotoPicker

actual val targetModule = module {
    single<PhotoPicker> { PhotoPicker() }
}
