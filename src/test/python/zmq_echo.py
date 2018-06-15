import time
import zmq


def publisher(port):
    context = zmq.Context()
    socket_ = context.socket(zmq.PUB)
    print(f"Serving on port: {port}")
    socket_.bind("tcp://*:%s" % port)
    return socket_


def subscriber(adress, topic=""):
    context = zmq.Context()
    socket_ = context.socket(zmq.SUB)
    print(f"Collecting updates from server: {adress}")
    socket_.connect(adress)
    socket_.setsockopt_string(zmq.SUBSCRIBE, topic)
    return socket_


def read(socket_):
    try:
        return socket_.recv(flags=zmq.NOBLOCK).split()
    except zmq.Again:
        return None, None


if __name__ == '__main__':
    import signal
    import sys

    mustExit = 0


    def signal_handler(signal, frame):
        print(f'{signal} caught. set flag to exit.')
        global mustExit
        mustExit = 1


    if len(sys.argv) != 3:
        print("Params: send_to_port listen_address")
        print(f"Current: {sys.argv}")
        exit(101)

    count = 0
    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    with publisher(sys.argv[1]) as pub, subscriber(sys.argv[2]) as sub:
        cnt = 0
        while mustExit == 0:
            val = read(sub)
            if len(val) == 2:
                msg = val[1]
            else:
                msg = val[0]
            if msg is not None:
                msg = msg.decode("utf-8")
                if "CLOSE" == msg:
                    print("Got close command")
                    break
                elif "TEST" == msg:
                    pub.send_string("HB")
                else:
                    pub.send_string(msg)
            print("loop")
            sys.stdout.flush()
            time.sleep(.3)
            cnt += 1
        print("Sending bye")
        pub.send_string("BYE")
    print("Exited")
    sys.stdout.flush()
