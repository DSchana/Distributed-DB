echo Building rapidjson for cmake config files
cd DMap/submodules/rapidjson
mkdir build
cd build
cmake ..
make install

echo Building DDB
cd ../../../..
mkdir build
cd build
cmake ..
make all
