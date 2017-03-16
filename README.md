# Extract all interfaces

######You can choose one or more packages and class files to extract interfaces.
---

###Installation
- Download com.successfactors.RefactorAll-master.zip to path(your local path)
- Extract to com.successfactors.RefactorAll
- Open eclipse - Help - install new software
- Click "Add..."
- Click "Archive..." and choose path/com.successfactors.RefactorAll-master/install.zip
- Restart eclipse

###Usage
Right click -> Refactor -> Extract Interfaces

###Tips
- This plugin will ignore all interface files to extract interface.
- If the class name is Sample.java, the new interface will be produced with the default name as ISample.java in the same package.You can set SystemProperty "FixType" as "Prefix" or "Suffix" and SystemProperty "FixValue" to change the name mode of the new interfaces.
- You can set SystemProperty "Overwrite" to determine whether the new interface file will be overwrite.

###Environment
- Eclipse: Neon
- JVM: 1.8
