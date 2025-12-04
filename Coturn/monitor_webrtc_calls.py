#!/usr/bin/env python3
"""
Real-time monitoring of WebRTC calls
Tracks logs to detect WebRTC activity
"""

import subprocess
import re
import time
import sys
from datetime import datetime

# Colors
GREEN = '\033[92m'
RED = '\033[91m'
YELLOW = '\033[93m'
BLUE = '\033[94m'
MAGENTA = '\033[95m'
CYAN = '\033[96m'
RESET = '\033[0m'

def print_header(msg):
    print(f"\n{BLUE}{'='*70}")
    print(f"  {msg}")
    print(f"{'='*70}{RESET}\n")

def print_event(event_type, msg, color=CYAN):
    timestamp = datetime.now().strftime('%H:%M:%S')
    print(f"{color}[{timestamp}] {event_type}: {msg}{RESET}")

def monitor_coturn_logs():
    """Real-time monitoring of Coturn logs"""
    print_header("MONITORING COTURN (STUN/TURN requests)")
    
    try:
        process = subprocess.Popen(
            ['docker', 'logs', '-f', 'nexy_coturn'],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )
        
        patterns = {
            'STUN': re.compile(r'STUN|Binding.*Request|Binding.*Response', re.IGNORECASE),
            'TURN': re.compile(r'TURN|Allocate.*Request|Allocate.*Response|Channel.*Bind', re.IGNORECASE),
            'AUTH': re.compile(r'401|auth|credential', re.IGNORECASE),
            'RELAY': re.compile(r'relay|allocation|channel', re.IGNORECASE),
            'ERROR': re.compile(r'error|fail|denied', re.IGNORECASE),
        }
        
        print_event("INFO", "Waiting for WebRTC activity... (Ctrl+C to exit)", YELLOW)
        
        for line in iter(process.stdout.readline, ''):
            line = line.strip()
            if not line:
                continue
            
            # Check patterns
            for pattern_name, pattern in patterns.items():
                if pattern.search(line):
                    if pattern_name == 'ERROR':
                        print_event(pattern_name, line, RED)
                    elif pattern_name == 'AUTH':
                        print_event(pattern_name, line, YELLOW)
                    elif pattern_name == 'TURN' or pattern_name == 'RELAY':
                        print_event(pattern_name, line, GREEN)
                    else:
                        print_event(pattern_name, line, CYAN)
                    break
        
    except KeyboardInterrupt:
        print_event("INFO", "Monitoring stopped", YELLOW)
    except Exception as e:
        print_event("ERROR", f"Monitoring error: {e}", RED)

def monitor_nexy_logs():
    """Monitoring Nexy Server logs for WebRTC messages"""
    print_header("MONITORING NEXY SERVER (WebRTC signals)")
    
    try:
        process = subprocess.Popen(
            ['docker', 'logs', '-f', 'messenger_server'],
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            bufsize=1
        )
        
        patterns = {
            'WEBRTC': re.compile(r'webrtc|ice|sdp|offer|answer|candidate', re.IGNORECASE),
            'CALL': re.compile(r'call|ring|accept|reject|hangup', re.IGNORECASE),
            'SIGNALING': re.compile(r'signaling|signal', re.IGNORECASE),
        }
        
        print_event("INFO", "Waiting for WebRTC signals... (Ctrl+C to exit)", YELLOW)
        
        for line in iter(process.stdout.readline, ''):
            line = line.strip()
            if not line:
                continue
            
            # Check patterns
            for pattern_name, pattern in patterns.items():
                if pattern.search(line):
                    if pattern_name == 'CALL':
                        print_event(pattern_name, line, MAGENTA)
                    elif pattern_name == 'SIGNALING':
                        print_event(pattern_name, line, GREEN)
                    else:
                        print_event(pattern_name, line, CYAN)
                    break
        
    except KeyboardInterrupt:
        print_event("INFO", "Monitoring stopped", YELLOW)
    except Exception as e:
        print_event("ERROR", f"Monitoring error: {e}", RED)

def check_recent_activity():
    """Checking recent activity in logs"""
    print_header("CHECKING RECENT ACTIVITY")
    
    # Check Coturn
    print_event("INFO", "Checking Coturn logs for the last minute...", BLUE)
    try:
        result = subprocess.run(
            ['docker', 'logs', '--since', '1m', 'nexy_coturn'],
            capture_output=True,
            text=True,
            timeout=5
        )
        
        if result.stdout:
            stun_count = result.stdout.count('STUN') + result.stdout.count('Binding')
            turn_count = result.stdout.count('TURN') + result.stdout.count('Allocate')
            
            if stun_count > 0:
                print_event("STUN", f"Detected {stun_count} STUN events", GREEN)
            if turn_count > 0:
                print_event("TURN", f"Detected {turn_count} TURN events", GREEN)
            
            if stun_count == 0 and turn_count == 0:
                print_event("INFO", "No WebRTC activity detected", YELLOW)
        else:
            print_event("INFO", "No new Coturn logs", YELLOW)
            
    except Exception as e:
        print_event("ERROR", f"Coturn check error: {e}", RED)
    
    # Check Nexy Server
    print_event("INFO", "Checking Nexy Server logs for the last minute...", BLUE)
    try:
        result = subprocess.run(
            ['docker', 'logs', '--since', '1m', 'messenger_server'],
            capture_output=True,
            text=True,
            timeout=5
        )
        
        if result.stdout:
            webrtc_keywords = ['webrtc', 'ice', 'sdp', 'offer', 'answer', 'candidate']
            activity = {kw: result.stdout.lower().count(kw) for kw in webrtc_keywords}
            
            total = sum(activity.values())
            if total > 0:
                print_event("WEBRTC", f"Detected {total} WebRTC events:", GREEN)
                for kw, count in activity.items():
                    if count > 0:
                        print(f"  - {kw}: {count}")
            else:
                print_event("INFO", "No WebRTC activity detected", YELLOW)
        else:
            print_event("INFO", "No new Nexy Server logs", YELLOW)
            
    except Exception as e:
        print_event("ERROR", f"Nexy Server check error: {e}", RED)

def show_usage():
    print(f"""
{BLUE}WebRTC Call Monitoring{RESET}

Usage:
  python monitor_webrtc_calls.py [mode]

Modes:
  coturn    - Monitoring Coturn (STUN/TURN requests)
  nexy      - Monitoring Nexy Server (WebRTC signals)
  check     - Checking recent activity (default)

Examples:
  python monitor_webrtc_calls.py check     # Check activity
  python monitor_webrtc_calls.py coturn    # Monitor Coturn
  python monitor_webrtc_calls.py nexy      # Monitor Nexy Server

{YELLOW}Testing Instructions:{RESET}
1. Run this script in monitoring mode
2. In the app, initiate a call between two users
3. Observe real-time events
""")

def main():
    if len(sys.argv) < 2:
        mode = 'check'
    else:
        mode = sys.argv[1].lower()
    
    if mode == 'help' or mode == '-h' or mode == '--help':
        show_usage()
        return 0
    
    print(f"{BLUE}╔═══════════════════════════════════════════════════════════════════╗")
    print(f"║        MONITORING WebRTC INTEGRATION COTURN + NEXY                 ║")
    print(f"╚═══════════════════════════════════════════════════════════════════╝{RESET}")
    
    if mode == 'coturn':
        monitor_coturn_logs()
    elif mode == 'nexy':
        monitor_nexy_logs()
    elif mode == 'check':
        check_recent_activity()
        print(f"\n{YELLOW}Hint: Use 'coturn' or 'nexy' modes for real-time monitoring{RESET}")
    else:
        print_event("ERROR", f"Unknown mode: {mode}", RED)
        show_usage()
        return 1
    
    return 0

if __name__ == '__main__':
    sys.exit(main())
