package com.example.dopapatch.data.sync

import java.time.Instant

/**
 * Last-write-wins conflict resolution. A remote row overwrites the local one only when it is
 * strictly newer by `updatedAt`; ties and older remotes keep the local copy (which, if dirty,
 * gets pushed on the next sync). Pure — unit-testable without Android/Room.
 */
fun shouldApplyRemote(localUpdatedAt: Instant?, remoteUpdatedAt: Instant): Boolean =
    localUpdatedAt == null || remoteUpdatedAt.isAfter(localUpdatedAt)
