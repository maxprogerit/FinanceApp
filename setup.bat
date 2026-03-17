@echo off
REM Smart Finance Dashboard - Setup Script

echo Creating project directory structure...

REM Create main source directories
mkdir src\main\java\com\smartfinance\dashboard\model 2>nul
mkdir src\main\java\com\smartfinance\dashboard\repository 2>nul
mkdir src\main\java\com\smartfinance\dashboard\service 2>nul
mkdir src\main\java\com\smartfinance\dashboard\controller 2>nul
mkdir src\main\java\com\smartfinance\dashboard\dto 2>nul
mkdir src\main\java\com\smartfinance\dashboard\config 2>nul
mkdir src\main\java\com\smartfinance\dashboard\util 2>nul

REM Create resources directories
mkdir src\main\resources\static\css 2>nul
mkdir src\main\resources\static\js 2>nul
mkdir src\main\resources\static\images 2>nul
mkdir src\main\resources\templates 2>nul

REM Create test directories
mkdir src\test\java\com\smartfinance\dashboard 2>nul
mkdir src\test\resources 2>nul

echo Directory structure created successfully!
echo.
echo Project structure:
tree /F /A

echo.
echo Setup complete! You can now build the project with: mvn clean install
