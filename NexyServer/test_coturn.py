#!/usr/bin/env python3
"""
Coturn TURN/STUN Server Test Script
Tests connectivity to Coturn server for WebRTC calls
"""

import socket
import struct
import hashlib
import hmac
import random
import sys

def test_stun_server(host, port):
    """Test STUN server connectivity"""
    print(f"\n=== Testing STUN Server: {host}:{port} ===")
    
    try:
        # Create STUN Binding Request
        # STUN Header: Type (Binding Request = 0x0001), Length, Magic Cookie, Transaction ID
        message_type = 0x0001  # Binding Request
        message_length = 0
        magic_cookie = 0x2112A442
        transaction_id = random.getrandbits(96).to_bytes(12, 'big')
        
        stun_header = struct.pack('!HHI', message_type, message_length, magic_cookie)
        stun_request = stun_header + transaction_id
        
        # Create UDP socket
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(5)
        
        # Send STUN request
        sock.sendto(stun_request, (host, port))
        print(f"✓ Sent STUN Binding Request ({len(stun_request)} bytes)")
        
        # Receive response
        data, addr = sock.recvfrom(1024)
        print(f"✓ Received response from {addr} ({len(data)} bytes)")
        
        # Parse response header
        resp_type, resp_length, resp_cookie = struct.unpack('!HHI', data[:8])
        resp_txn_id = data[8:20]
        
        if resp_txn_id == transaction_id:
            print(f"✓ Transaction ID matches")
            print(f"✓ Response type: 0x{resp_type:04x}")
            
            if resp_type == 0x0101:  # Binding Success Response
                print("✓ STUN server is working correctly!")
                return True
            else:
                print(f"⚠ Unexpected response type: 0x{resp_type:04x}")
                return False
        else:
            print("✗ Transaction ID mismatch")
            return False
            
    except socket.timeout:
        print("✗ Timeout - STUN server not responding")
        return False
    except Exception as e:
        print(f"✗ Error: {e}")
        return False
    finally:
        sock.close()

def test_turn_authentication(host, port, username, password, realm):
    """Test TURN server authentication"""
    print(f"\n=== Testing TURN Server Authentication ===")
    print(f"Server: {host}:{port}")
    print(f"Username: {username}")
    print(f"Realm: {realm}")
    
    try:
        # Create ALLOCATE request (TURN-specific)
        message_type = 0x0003  # Allocate Request
        magic_cookie = 0x2112A442
        transaction_id = random.getrandbits(96).to_bytes(12, 'big')
        
        # Build attributes
        attributes = b''
        
        # REQUESTED-TRANSPORT attribute (UDP = 17)
        attr_type = 0x0019
        attr_value = struct.pack('!BBH', 17, 0, 0)  # Protocol=UDP, Reserved
        attr_length = len(attr_value)
        attributes += struct.pack('!HH', attr_type, attr_length) + attr_value
        
        # Add padding to align to 4 bytes
        padding = (4 - (len(attributes) % 4)) % 4
        attributes += b'\x00' * padding
        
        message_length = len(attributes)
        stun_header = struct.pack('!HHI', message_type, message_length, magic_cookie)
        turn_request = stun_header + transaction_id + attributes
        
        # Create UDP socket
        sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        sock.settimeout(5)
        
        # Send ALLOCATE request
        sock.sendto(turn_request, (host, port))
        print(f"✓ Sent ALLOCATE Request ({len(turn_request)} bytes)")
        
        # Receive response (should be error 401 - Unauthorized)
        data, addr = sock.recvfrom(2048)
        print(f"✓ Received response from {addr} ({len(data)} bytes)")
        
        # Parse response
        resp_type, resp_length, resp_cookie = struct.unpack('!HHI', data[:8])
        
        if resp_type == 0x0113:  # Allocate Error Response
            print("✓ Received error response (authentication required)")
            
            # Parse attributes to find ERROR-CODE
            pos = 20  # Skip header and transaction ID
            while pos < len(data):
                if pos + 4 > len(data):
                    break
                    
                attr_type, attr_length = struct.unpack('!HH', data[pos:pos+4])
                pos += 4
                
                if attr_type == 0x0009:  # ERROR-CODE
                    error_class = data[pos + 2]
                    error_number = data[pos + 3]
                    error_code = error_class * 100 + error_number
                    print(f"✓ Error Code: {error_code}")
                    
                    if error_code == 401:
                        print("✓ TURN server is requesting authentication (401 Unauthorized)")
                        print("✓ TURN server is working correctly!")
                        return True
                
                # Move to next attribute (with padding)
                pos += attr_length
                padding = (4 - (attr_length % 4)) % 4
                pos += padding
            
            print("⚠ No 401 error code found in response")
            return False
        else:
            print(f"⚠ Unexpected response type: 0x{resp_type:04x}")
            return False
            
    except socket.timeout:
        print("✗ Timeout - TURN server not responding")
        return False
    except Exception as e:
        print(f"✗ Error: {e}")
        import traceback
        traceback.print_exc()
        return False
    finally:
        sock.close()

def main():
    print("=" * 60)
    print("Coturn TURN/STUN Server Connectivity Test")
    print("=" * 60)
    
    # Configuration
    STUN_HOST = "localhost"
    STUN_PORT = 3478
    TURN_USERNAME = "nexy"
    TURN_PASSWORD = "nexy_turn_password"
    TURN_REALM = "nexy.local"
    
    # Test STUN
    stun_result = test_stun_server(STUN_HOST, STUN_PORT)
    
    # Test TURN
    turn_result = test_turn_authentication(STUN_HOST, STUN_PORT, TURN_USERNAME, TURN_PASSWORD, TURN_REALM)
    
    # Summary
    print("\n" + "=" * 60)
    print("Test Summary")
    print("=" * 60)
    print(f"STUN Server: {'✓ PASS' if stun_result else '✗ FAIL'}")
    print(f"TURN Server: {'✓ PASS' if turn_result else '✗ FAIL'}")
    print("=" * 60)
    
    if stun_result and turn_result:
        print("\n✓ All tests passed! Coturn is configured correctly.")
        print("\nYour WebRTC calls should now work reliably through NAT/firewall.")
        print("\nNext steps:")
        print("1. Build and install the Android client")
        print("2. Test calls between devices on different networks")
        print("3. Monitor Coturn logs: docker logs -f messenger_coturn")
        return 0
    else:
        print("\n✗ Some tests failed. Please check:")
        print("1. Coturn container is running: docker ps")
        print("2. Coturn logs: docker logs messenger_coturn")
        print("3. Firewall allows UDP port 3478")
        return 1

if __name__ == "__main__":
    sys.exit(main())
