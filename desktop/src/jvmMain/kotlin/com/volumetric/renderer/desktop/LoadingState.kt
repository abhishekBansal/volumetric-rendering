package com.volumetric.renderer.desktop

/**
 * Represents the state of dataset loading operations.
 */
sealed class LoadingState {
    /**
     * No loading operation in progress
     */
    object Idle : LoadingState()
    
    /**
     * Loading in progress
     * @param progress Loading progress from 0.0 to 1.0
     * @param message Current loading operation message
     */
    data class Loading(val progress: Float, val message: String) : LoadingState()
    
    /**
     * Loading failed with an error
     * @param message User-friendly error message
     * @param exception Original exception if available
     */
    data class Error(val message: String, val exception: Exception? = null) : LoadingState()
    
    /**
     * Loading completed successfully
     */
    object Success : LoadingState()
}
