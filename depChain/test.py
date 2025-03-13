#!/usr/bin/env python3
import subprocess
import os
import time
import shutil

# Define the base command for the server
server_base_command = 'mvn exec:java -Dexec.mainClass="depchain.blockchain.BlockchainMember" -Dexec.args='
# Define the base command for the client
client_command = 'mvn exec:java -Dexec.mainClass="depchain.client.DepChainClient" -Dexec.args="5 9001"'
# Define the server arguments
server_args = [
    '"1 8001"',
    '"2 8002"',
    '"3 8003"',
    '"4 8004"'
]

# Session names for each process
session_names = [f"depchain-server-{i+1}" for i in range(len(server_args))]
session_names.append("depchain-client")

# Check if tmux is installed
def check_tmux():
    if not shutil.which("tmux"):
        raise Exception("tmux is not installed. Please install tmux to use this script.")

# Detect available terminal emulator
def detect_terminal():
    if shutil.which("alacritty"):
        return "alacritty"
    elif shutil.which("gnome-terminal"):
        return "gnome-terminal"
    else:
        raise Exception("No supported terminal emulator found (Alacritty or Gnome Terminal).")

# Function to create a new tmux session and run a command
def create_tmux_session(session_name, command):
    # First, check if session already exists
    result = subprocess.run(
        ['tmux', 'has-session', '-t', session_name], 
        stderr=subprocess.PIPE, 
        stdout=subprocess.PIPE
    )
    
    if result.returncode == 0:
        # Session exists, send C-c to interrupt current process
        subprocess.run(['tmux', 'send-keys', '-t', session_name, 'C-c', 'C-c', 'Enter'])
        time.sleep(1)  # Give time for the process to terminate
        # Clear the screen and run the new command
        subprocess.run(['tmux', 'send-keys', '-t', session_name, 'clear', 'Enter'])
        subprocess.run(['tmux', 'send-keys', '-t', session_name, command, 'Enter'])
    else:
        # Create a new session
        subprocess.run(['tmux', 'new-session', '-d', '-s', session_name])
        # Send the command to the session
        subprocess.run(['tmux', 'send-keys', '-t', session_name, command, 'Enter'])
        
        # Open the session in a new terminal window
        terminal = detect_terminal()
        if terminal == "alacritty":
            subprocess.Popen(['alacritty', '-e', 'tmux', 'attach-session', '-t', session_name])
        elif terminal == "gnome-terminal":
            subprocess.Popen(['gnome-terminal', '--', 'tmux', 'attach-session', '-t', session_name])

# Function to launch all servers and the client
def launch_processes():
    print("Launching servers and client...")
    
    for i, args in enumerate(server_args):
        server_command = f'{server_base_command}{args}'
        create_tmux_session(session_names[i], server_command)
    
    create_tmux_session(session_names[-1], client_command)
    print("All processes have been launched.")

# Function to interrupt all running processes
def interrupt_processes():
    print("Interrupting all running processes...")
    
    for session in session_names:
        # Check if session exists before sending keys
        result = subprocess.run(
            ['tmux', 'has-session', '-t', session], 
            stderr=subprocess.PIPE, 
            stdout=subprocess.PIPE
        )
        
        if result.returncode == 0:
            # Send Ctrl+C twice to interrupt the process
            subprocess.run(['tmux', 'send-keys', '-t', session, 'C-c', 'C-c', 'Enter'])
    
    print("All processes have been interrupted.")

# Function to kill all tmux sessions
def kill_all_sessions():
    print("Terminating all tmux sessions...")
    
    for session in session_names:
        # Check if session exists before killing
        result = subprocess.run(
            ['tmux', 'has-session', '-t', session], 
            stderr=subprocess.PIPE, 
            stdout=subprocess.PIPE
        )
        
        if result.returncode == 0:
            subprocess.run(['tmux', 'kill-session', '-t', session])
    
    print("All tmux sessions have been terminated.")

# Check if tmux is installed
check_tmux()

# Launch processes initially
launch_processes()

# Keep the original terminal open and provide options
while True:
    user_input = input(
        "Type 'interrupt' to interrupt processes, 'restart' to interrupt and restart, 'kill' to terminate sessions, or 'exit' to quit: "
    ).strip().lower()
    
    if user_input == 'interrupt':
        interrupt_processes()
    elif user_input == 'restart':
        interrupt_processes()
        time.sleep(1)  # Give time for processes to terminate
        launch_processes()
    elif user_input == 'kill':
        kill_all_sessions()
    elif user_input == 'exit':
        print("Exiting without killing sessions.")
        break
    else:
        print("Invalid input. Please type 'interrupt', 'restart', 'kill', or 'exit'.")