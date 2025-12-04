#!/usr/bin/env python3
"""
Comprehensive integration test for Coturn with Nexy application
Checks:
1. Availability of Coturn server
2. API endpoint for getting ICE servers
3. Validation of ICE server configuration
4. WebRTC system readiness
"""

import socket
import struct
import sys
import time
import requests
import json

# Colors for output
GREEN = '\033[92m'
RED = '\033[91m'
YELLOW = '\033[93m'
BLUE = '\033[94m'
RESET = '\033[0m'

def print_success(msg):
    print(f"{GREEN}✓ {msg}{RESET}")

def print_error(msg):
    print(f"{RED}✗ {msg}{RESET}")

def print_info(msg):
    print(f"{BLUE}ℹ {msg}{RESET}")

def print_warning(msg):
    print(f"{YELLOW}⚠ {msg}{RESET}")

def test_coturn_stun(host='localhost', port=3478):
    """Test Coturn STUN server"""
    print_info(f"Testing STUN server at {host}:{port}...")
    
    try:
        # STUN Binding Request
        transaction_id = b'\x00' * 12
        message_type = struct.pack('!H', 0x0001)  # Binding Request
        message_length = struct.pack('!H', 0)
        magic_cookie = struct.pack('!I', 0x2112A442)
        
        stun_request = message_type + message_length + magic_cookie + transaction_id
        
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(5)
        sock.sendto(stun_request, (host, port))
        
        response, _ = sock.recvfrom(1024)
        sock.close()
        
        response_type = struct.unpack('!H', response[:2])[0]
        
        if response_type == 0x0101:  # Binding Success Response
            print_success(f"STUN server responds correctly")
            return True
        else:
            print_error(f"STUN server returned unexpected response: {hex(response_type)}")
            return False
            
    except socket.timeout:
        print_error("STUN request timed out")
        return False
    except Exception as e:
        print_error(f"STUN test error: {e}")
        return False

def test_coturn_turn(host='localhost', port=3478, username='nexy', password='nexy_turn_password'):
    """Test Coturn TURN server"""
    print_info(f"Testing TURN server at {host}:{port}...")
    
    try:
        # TURN Allocate Request (without authentication to check 401)
        transaction_id = b'\x00' * 12
        message_type = struct.pack('!H', 0x0003)  # Allocate Request
        message_length = struct.pack('!H', 0)
        magic_cookie = struct.pack('!I', 0x2112A442)
        
        turn_request = message_type + message_length + magic_cookie + transaction_id
        
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(5)
        sock.sendto(turn_request, (host, port))
        
        response, _ = sock.recvfrom(1024)
        sock.close()
        
        response_type = struct.unpack('!H', response[:2])[0]
        
        if response_type == 0x0113:  # Allocate Error Response (401)
            print_success(f"TURN server requires authentication (expected behavior)")
            return True
        else:
            print_warning(f"TURN server returned response: {hex(response_type)}")
            return True  # Possible successful allocation
            
    except socket.timeout:
        print_error("TURN request timed out")
        return False
    except Exception as e:
        print_error(f"TURN test error: {e}")
        return False

def test_api_endpoint(base_url='http://localhost:8080', token=None):
    """Test API endpoint for getting ICE servers"""
    print_info(f"Testing API endpoint {base_url}/api/turn/ice-servers...")
    
    try:
        headers = {}
        if token:
            headers['Authorization'] = f'Bearer {token}'
        
        response = requests.get(f'{base_url}/api/turn/ice-servers', headers=headers, timeout=5)
        
        if response.status_code == 401 and not token:
            print_warning("API requires authentication (token not provided)")
            print_info("For full testing, provide a valid JWT token")
            return None
        
        if response.status_code != 200:
            print_error(f"API returned status {response.status_code}")
            return None
        
        data = response.json()
        print_success(f"API endpoint is accessible")
        print_info(f"API response:\n{json.dumps(data, indent=2)}")
        
        return data
        
    except requests.exceptions.ConnectionError:
        print_error(f"Failed to connect to {base_url}")
        return None
    except Exception as e:
        print_error(f"API test error: {e}")
        return None

def validate_ice_config(ice_config):
    """Validation of ICE server configuration"""
    if not ice_config:
        return False
    
    print_info("Validating ICE server configuration...")
    
    if 'iceServers' not in ice_config:
        print_error("Missing 'iceServers' field")
        return False
    
    ice_servers = ice_config['iceServers']
    
    if not isinstance(ice_servers, list) or len(ice_servers) == 0:
        print_error("iceServers should be a non-empty array")
        return False
    
    has_stun = False
    has_turn = False
    
    for idx, server in enumerate(ice_servers):
        if 'urls' not in server:
            print_error(f"Server #{idx} does not contain 'urls' field")
            return False
        
        urls = server['urls']
        if not isinstance(urls, list):
            urls = [urls]
        
        for url in urls:
            if url.startswith('stun:'):
                has_stun = True
                print_success(f"Found STUN server: {url}")
            elif url.startswith('turn:'):
                has_turn = True
                if 'username' in server and 'credential' in server:
                    print_success(f"Found TURN server: {url} (with authentication)")
                else:
                    print_warning(f"Found TURN server: {url} (without authentication)")
    
    if not has_stun:
        print_warning("STUN server not found in configuration")
    
    if not has_turn:
        print_warning("TURN server not found in configuration")
    
    if has_stun and has_turn:
        print_success("Configuration contains both STUN and TURN servers")
        return True
    
    return has_stun or has_turn

def test_server_logs():
    """Check server logs via Docker"""
    print_info("Checking server logs...")
    
    try:
        import subprocess
        
        # Check Coturn logs
        print_info("Latest Coturn events:")
        result = subprocess.run(
            ['docker', 'logs', 'nexy_coturn', '--tail', '10'],
            capture_output=True,
            text=True,
            timeout=5
        )
        
        if result.returncode == 0:
            print(result.stdout[-500:] if len(result.stdout) > 500 else result.stdout)
            print_success("Coturn logs retrieved")
        else:
            print_warning("Failed to retrieve Coturn logs")
        
        # Check Nexy Server logs
        print_info("\nLatest Nexy Server events:")
        result = subprocess.run(
            ['docker', 'logs', 'messenger_server', '--tail', '10'],
            capture_output=True,
            text=True,
            timeout=5
        )
        
        if result.returncode == 0:
            print(result.stdout[-500:] if len(result.stdout) > 500 else result.stdout)
            print_success("Nexy Server logs retrieved")
        else:
            print_warning("Failed to retrieve Nexy Server logs")
            
    except Exception as e:
        print_warning(f"Failed to check logs: {e}")

def test_docker_containers():
    """Check status of Docker containers"""
    print_info("Checking status of Docker containers...")
    
    try:
        import subprocess
        
        result = subprocess.run(
            ['docker', 'ps', '--filter', 'name=nexy_coturn', '--filter', 'name=messenger_server', 
             '--format', '{{.Names}}\t{{.Status}}\t{{.Ports}}'],
            capture_output=True,
            text=True,
            timeout=5
        )
        
        if result.returncode == 0:
            print(result.stdout)
            
            if 'nexy_coturn' in result.stdout and 'Up' in result.stdout:
                print_success("Coturn container is running")
            else:
                print_error("Coturn container is not running")
                return False
            
            if 'messenger_server' in result.stdout and 'Up' in result.stdout:
                print_success("Nexy Server container is running")
            else:
                print_error("Nexy Server container is not running")
                return False
            
            return True
        else:
            print_error("Failed to check container status")
            return False
            
    except Exception as e:
        print_error(f"Container check error: {e}")
        return False

def main():
    print(f"\n{BLUE}{'='*60}")
    print(f"  Testing Coturn integration with Nexy application")
    print(f"{'='*60}{RESET}\n")
    
    token = None
    if len(sys.argv) > 1:
        token = sys.argv[1]
        print_info(f"Using authentication token")
    else:
        print_warning("Token not provided. Some tests may be limited")
        print_info("Usage: python test_webrtc_integration.py [JWT_TOKEN]\n")
    
    results = {}
    
    # 1. Check Docker containers
    print(f"\n{BLUE}[1/6] Checking Docker containers{RESET}")
    results['docker'] = test_docker_containers()
    
    # 2. Test STUN server
    print(f"\n{BLUE}[2/6] Testing STUN server{RESET}")
    results['stun'] = test_coturn_stun()
    
    # 3. Test TURN server
    print(f"\n{BLUE}[3/6] Testing TURN server{RESET}")
    results['turn'] = test_coturn_turn()
    
    # 4. Test API endpoint
    print(f"\n{BLUE}[4/6] Testing API endpoint{RESET}")
    ice_config = test_api_endpoint(token=token)
    results['api'] = ice_config is not None
    
    # 5. Validate ICE configuration
    print(f"\n{BLUE}[5/6] Validating ICE configuration{RESET}")
    results['ice_config'] = validate_ice_config(ice_config)
    
    # 6. Check logs
    print(f"\n{BLUE}[6/6] Checking server logs{RESET}")
    test_server_logs()
    
    # Final report
    print(f"\n{BLUE}{'='*60}")
    print(f"  FINAL REPORT")
    print(f"{'='*60}{RESET}\n")
    
    total_tests = len([r for r in results.values() if r is not None])
    passed_tests = sum([1 for r in results.values() if r is True])
    
    print(f"Total tests: {total_tests}")
    print(f"Passed: {GREEN}{passed_tests}{RESET}")
    print(f"Failed: {RED}{total_tests - passed_tests}{RESET}")
    
    if passed_tests == total_tests and total_tests > 0:
        print(f"\n{GREEN}{'='*60}")
        print(f"  ✓ ALL TESTS PASSED SUCCESSFULLY!")
        print(f"  WebRTC integration is ready for use")
        print(f"{'='*60}{RESET}\n")
        return 0
    else:
        print(f"\n{YELLOW}{'='*60}")
        print(f"  ⚠ Some tests failed")
        print(f"  Check the logs above for details")
        print(f"{'='*60}{RESET}\n")
        return 1

if __name__ == '__main__':
    sys.exit(main())
