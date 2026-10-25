@echo off
if exist jre\bin\javaw.exe goto jre
start /b javaw -Djdk.util.zip.disableZip64ExtraFieldValidation=true -jar dbgl.jar
goto end

:jre
start /b jre\bin\javaw -Djdk.util.zip.disableZip64ExtraFieldValidation=true -jar dbgl.jar

:end
