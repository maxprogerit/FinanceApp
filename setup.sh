#!/bin/bash
# Smart Finance Dashboard - Complete Setup Script

echo "Creating Smart Finance Dashboard Directory Structure..."

# Create all directories
mkdir -p src/main/java/com/smartfinance/dashboard/{model,repository,service,controller,dto,config,util}
mkdir -p src/main/resources/{static/{css,js,images},templates}
mkdir -p src/test/java/com/smartfinance/dashboard

echo "Directory structure created!"
echo ""
echo "✅ Project structure ready"
echo "✅ Next: Generate application files"
