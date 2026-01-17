package com.volumetric.renderer.core.math

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.math.sqrt

class Vector3Test {

    @Test
    fun testVectorAddition() {
        val v1 = Vector3(1f, 2f, 3f)
        val v2 = Vector3(4f, 5f, 6f)
        val result = v1 + v2
        
        assertEquals(5f, result.x)
        assertEquals(7f, result.y)
        assertEquals(9f, result.z)
    }

    @Test
    fun testVectorNormalization() {
        val v = Vector3(3f, 0f, 0f)
        val result = v.normalize()
        
        assertEquals(1f, result.x)
        assertEquals(0f, result.y)
        assertEquals(0f, result.z)
    }
}
