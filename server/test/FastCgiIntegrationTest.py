#!/usr/bin/env python3
"""Tests the real Java server and supplied FastCGI library on disposable local ports.
Compile server/src/*.java and server/test/*.java before running. No Apache or pip needed.
"""
import concurrent.futures
import json
import os
from pathlib import Path
import socket
import struct
import subprocess
import time
import unittest

ROOT = Path(__file__).resolve().parents[2]
CLASSES = Path(os.environ.get('LAB2_CLASSES_DIR', ROOT / 'server/build/classes'))
TEST_CLASSES = ROOT / 'server/build/test-classes'
CLASSPATH = os.pathsep.join(map(str, (CLASSES, TEST_CLASSES, ROOT / 'server/lib/fastcgi-lib.jar')))


def record(kind, data=b''):
    return struct.pack('!BBHHBB', 1, kind, 1, len(data), 0, 0) + data


def length(n):
    return bytes([n]) if n < 128 else struct.pack('!I', n | 0x80000000)


def request_bytes(query, method='GET'):
    params = b''
    for key, value in {'REQUEST_METHOD': method, 'QUERY_STRING': query, 'CONTENT_LENGTH': '0'}.items():
        key, value = key.encode(), value.encode()
        params += length(len(key)) + length(len(value)) + key + value
    chunks = b''.join(record(4, params[i:i + 60000]) for i in range(0, len(params), 60000))
    return record(1, struct.pack('!HB5x', 1, 0)) + chunks + record(4) + record(5)


def read_exact(sock, n):
    data = b''
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise EOFError('Connection ended before a complete FastCGI response')
        data += chunk
    return data


class FastCgiIntegrationTest(unittest.TestCase):
    def setUp(self):
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            self.port = sock.getsockname()[1]
        self.server = subprocess.Popen(
            ['java', '-Xmx128m', f'-DFCGI_PORT={self.port}', '-cp', CLASSPATH, 'Main'],
            stdout=subprocess.DEVNULL, stderr=subprocess.PIPE,
        )
        self.addCleanup(self.stop_server)

    def stop_server(self):
        if self.server.poll() is None:
            self.server.terminate()
        try:
            self.server.wait(timeout=3)
        except subprocess.TimeoutExpired:
            self.server.kill()
            self.server.wait()
        self.server.stderr.close()

    def connect(self):
        # A readiness check must send a real request; empty connections are a test case.
        deadline = time.monotonic() + 3
        while True:
            try:
                sock = socket.create_connection(('127.0.0.1', self.port), timeout=3)
                sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
                return sock
            except ConnectionRefusedError:
                if self.server.poll() is not None or time.monotonic() >= deadline:
                    raise
                time.sleep(.02)

    def request(self, query='x=1&y=-0.5&r=2.5', method='GET', chunk_size=None):
        with self.connect() as sock:
            payload = request_bytes(query, method)
            if chunk_size:
                for i in range(0, len(payload), chunk_size):
                    sock.sendall(payload[i:i + chunk_size])
            else:
                sock.sendall(payload)
            output = b''
            while True:
                version, kind, request_id, size, padding, _ = struct.unpack('!BBHHBB', read_exact(sock, 8))
                self.assertEqual(version, 1)
                self.assertEqual(request_id, 1)
                data = read_exact(sock, size)
                read_exact(sock, padding)
                if kind == 6:
                    output += data
                if kind == 3:
                    break
        header, body = output.split(b'\r\n\r\n', 1)
        headers = dict(line.split(': ', 1) for line in header.decode().split('\r\n'))
        self.assertEqual(int(headers['Content-Length']), len(body))
        self.assertEqual(headers['Content-Type'], 'application/json; charset=UTF-8')
        return int(headers['Status'].split()[0]), json.loads(body), headers

    def assert_healthy(self):
        status, body, _ = self.request()
        self.assertEqual(status, 200)
        self.assertEqual((body['x'], body['y'], body['r'], body['hit']), (1, '-0.5', 2.5, True))
        self.assertIsNone(self.server.poll())

    def test_recovery_after_truncated_request(self):
        payload = request_bytes('x=1&y=-0.5&r=2.5')
        # Includes failure of the FIRST request and repeated failures after successful ones.
        for size in (0, 1, 4, 7, 8, 10, 15, 16, 20, 40, len(payload) - 16):
            with self.subTest(bytes_sent=size):
                with self.connect() as sock:
                    if size:
                        sock.sendall(payload[:size])
                self.assert_healthy()

    def test_recovery_after_invalid_protocol(self):
        # This exercises FCGIaccept returning -1, not just throwing an exception.
        with self.connect() as sock:
            sock.sendall(b'GET / HTTP/1.1\r\n\r\n')
        self.assert_healthy()

    def test_recovery_after_connection_reset(self):
        with self.connect() as sock:
            sock.sendall(request_bytes('x=1&y=0&r=2')[:20])
            sock.setsockopt(socket.SOL_SOCKET, socket.SO_LINGER, struct.pack('ii', 1, 0))
        self.assert_healthy()

    def test_control_characters_are_rejected(self):
        for name, number in (('x', '1'), ('y', '0'), ('r', '2')):
            for code in (*range(32), 127):
                with self.subTest(parameter=name, code=code):
                    encoded = f'%{code:02X}'
                    query = 'x=1&y=0&r=2'.replace(f'{name}={number}', f'{name}={encoded}{number}{encoded}')
                    self.assertEqual(self.request(query)[0], 400)
        self.assert_healthy()

    def test_bounded_numbers_and_query(self):
        for query in ('x=' + '0' * 257 + '&y=0&r=2',
                      'x=0&y=0.' + '1' * 255 + '&r=2',
                      'x=0&y=0&r=2.' + '0' * 255,
                      'x=0&y=0&r=2.' + '0' * 200000):
            with self.subTest(query_length=len(query)):
                self.assertEqual(self.request(query)[0], 400)
                self.assert_healthy()
        for query in ('x=0&y=0.' + '1' * 254 + '&r=2',
                      'x=0&y=0&r=2.' + '0' * 254,
                      'x=%201%20&y=%20-0.5%20&r=%202.5%20'):
            self.assertEqual(self.request(query)[0], 200)

    def test_fragmentation_and_methods(self):
        for size in (1, 7, 16):
            with self.subTest(chunk_size=size):
                self.assertEqual(self.request(chunk_size=size)[0], 200)
        for method in ('POST', 'PUT', 'DELETE', 'PATCH', 'OPTIONS'):
            status, body, headers = self.request(method=method)
            self.assertEqual(status, 405)
            self.assertEqual(headers['Allow'], 'GET')
        self.assert_healthy()

    def test_parallel_requests_keep_their_coordinates(self):
        self.assert_healthy()
        def check(i):
            x, y = i % 9 - 3, f'0.{i:03d}'
            status, body, _ = self.request(f'x={x}&y={y}&r=2')
            self.assertEqual(status, 200)
            self.assertEqual((body['x'], body['y']), (x, y))
        with concurrent.futures.ThreadPoolExecutor(max_workers=12) as pool:
            list(pool.map(check, range(300)))
        self.assert_healthy()

    def test_json_string_round_trip(self):
        encoded = subprocess.check_output(['java', '-cp', CLASSPATH, 'JsonStringProbe'], timeout=5)
        expected = 'Кавычка: "; слеш: \\' + ''.join(map(chr, range(32)))
        self.assertEqual(json.loads(encoded), expected)


if __name__ == '__main__':
    unittest.main(verbosity=2)
