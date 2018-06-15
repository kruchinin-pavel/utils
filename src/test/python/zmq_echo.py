import time
import sys
from sys import exit
import signal

import zmq


class Java:
    cmds = {}
    close_code = 0

    def add_cmd(self, cmd, func):
        self.cmds[cmd] = func

    def __init__(self, send_to_port, listen_from_address, default_cmd=lambda java_, msg: java_.send(msg)):
        self.pub = self.publisher(send_to_port)
        self.sub = self.subscriber(listen_from_address)
        self.add_cmd("TEST", lambda java_, cmd: java_.send("HB"))
        self.add_cmd("CLOSE", lambda java_, cmd: exit(0))
        self.default_cmd = default_cmd

    def run(self):
        while self.close_code == 0:
            val = self.read()
            if len(val) == 2:
                msg = val[1]
            else:
                msg = val[0]
            if msg is not None:
                msg = msg.decode("utf-8")
                cmd = msg.split(" ")[0]
                if cmd in self.cmds:
                    print(f"Processing command: {cmd}")
                    self.cmds[cmd](self, msg)
                else:
                    print(f"Processing default command")
                    self.default_cmd(self, msg)
            sys.stdout.flush()
            time.sleep(.3)
            sys.stdout.flush()
        print(f"Java completed: close_code={self.close_code}")

    def read(self):
        try:
            return self.sub.recv(flags=zmq.NOBLOCK).split()
        except zmq.Again:
            return None, None

    def send(self, msg):
        self.pub.send_string(msg)

    def close(self):
        self.close_code = 1

    def __enter__(self):
        self.pub.__enter__()
        self.sub.__enter__()
        return self

    def __exit__(self, *args, **kwargs):
        self.term()
        self.pub.__exit__(args, kwargs)
        self.sub.__exit__(args, kwargs)

    def term(self):
        self.close()
        self.pub.send_string("BYE")

    @staticmethod
    def publisher(port):
        context = zmq.Context()
        socket_ = context.socket(zmq.PUB)
        print(f"Serving on port: {port}")
        socket_.bind("tcp://*:%s" % port)
        return socket_

    @staticmethod
    def subscriber(adress, topic=""):
        context = zmq.Context()
        socket_ = context.socket(zmq.SUB)
        print(f"Collecting updates from server: {adress}")
        socket_.connect(adress)
        socket_.setsockopt_string(zmq.SUBSCRIBE, topic)
        return socket_


def register():
    if len(sys.argv) != 3:
        print("Params: send_to_port listen_address")
        print(f"Current: {sys.argv}")
        exit(101)
    signal.signal(signal.SIGINT, lambda: exit(0))
    signal.signal(signal.SIGTERM, lambda: exit(0))
    return Java(sys.argv[1], sys.argv[2])


if __name__ == '__main__':
    with register() as java:
        java.run()

    print("Exited")
    sys.stdout.flush()
