package pl.lukaszburzak.creye.ide

import com.intellij.testFramework.LightVirtualFile

/** Marker virtual file that backs the dependency graph editor tab. */
class DependencyGraphVirtualFile : LightVirtualFile("Dependency Graph") {
    init {
        isWritable = false
    }
}
