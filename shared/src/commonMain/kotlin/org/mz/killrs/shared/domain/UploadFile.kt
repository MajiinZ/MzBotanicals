package org.mz.killrs.shared.domain

/** Platform picker supplies a bounded, asynchronous reader. */
class UploadFile(val read: suspend () -> ByteArray)
