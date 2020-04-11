echo Building DDB
mkdir build
cd build
cmake ..
make all

echo Creating config files
cp -r ../.config.default ./.config
