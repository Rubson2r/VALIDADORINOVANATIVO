@echo off
echo Limpando cache e rebuilding projeto Android...
echo.

echo 1. Limpando build cache...
gradlew.bat clean

echo.
echo 2. Limpando cache do Gradle...
gradlew.bat cleanBuildCache

echo.
echo 3. Invalidando cache do Android Studio...
echo Execute no Android Studio: File -> Invalidate Caches and Restart

echo.
echo 4. Removendo pastas de cache locais...
if exist ".gradle" rmdir /s /q ".gradle"
if exist "app\build" rmdir /s /q "app\build"
if exist "build" rmdir /s /q "build"

echo.
echo 5. Rebuilding projeto...
gradlew.bat build

echo.
echo Limpeza concluída! 
echo Agora abra o Android Studio e sincronize o projeto.
pause