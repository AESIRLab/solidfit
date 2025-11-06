package com.example.solidfit.data

import android.`annotation`.SuppressLint
import android.content.Context
import java.io.File
import kotlin.String
import kotlin.jvm.Volatile

public class WorkoutItemDatabase(
  private val baseUri: String,
  private val baseDir: File,
  private var webId: String?,
) {
  public fun WorkoutItemDao(): WorkoutItemDao = WorkoutItemDaoImpl(baseUri, baseDir, webId)

  public companion object {
    @SuppressLint
    @Volatile
    private var INSTANCE: WorkoutItemDatabase? = null

    public fun getDatabase(
      context: Context,
      baseUri: String,
      webId: String? = null,
    ): WorkoutItemDatabase {
      if (INSTANCE != null) {
        return INSTANCE!!
      } else {
        synchronized(this) {
        val instance = WorkoutItemDatabase(baseUri, context.filesDir, webId)

        INSTANCE = instance
        return instance
        }
        }
      }
    }
  }
