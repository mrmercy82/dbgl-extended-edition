@echo off
if exist jre\bin\java.exe goto jre
java -Djava.library.path=lib -classpath dbgl.jar org.dbgl.gui.SendToProfile %1
goto end

:jre
jre\bin\java -Djava.library.path=lib -classpath dbgl.jar org.dbgl.gui.SendToProfile %1

:end
