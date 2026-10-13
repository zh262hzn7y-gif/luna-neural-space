plugins {
    id("com.android.application") version "8.7.0" apply false
    id("org." + chr(106)+chr(101)+chr(116)+chr(98)+chr(114)+chr(97)+chr(105)+chr(110)+chr(115)+chr(46)+chr(107)+chr(111)+chr(116)+chr(108)+chr(105)+chr(110)+chr(46)+chr(97)+chr(110)+chr(100)+chr(114)+chr(111)+chr(105)+chr(100)) version "2.0.21" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
}
