echo Building DDB
mkdir build
cd build
cmake ..
make all

echo Creating config file
cp ../ddb.config.default ./ddb.config
