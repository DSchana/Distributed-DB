#include <arpa/inet.h>
#include <future>
#include <netinet/in.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <sys/wait.h>
#include <vector>
#include <mutex>

class NetworkManager {
    bool running;

    std::vector<std::future<int> > connections;
    std::mutex connection_mutex;
    std::mutex data_recv_mutex;

    int sock;
    struct sockaddr_in dst;
    struct sockaddr_in serv;
    socklen_t sock_size = sizeof(struct sockaddr_in);

    int handleConnection();

public:
    NetworkManager();
    ~NetworkManager();

    int start();
    int stop();
};
