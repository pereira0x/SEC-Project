#!/usr/bin/env python3

import subprocess
import os
import signal
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

# Store the process IDs of the spawned terminals
pids = []

# Detect available terminal emulator
def detect_terminal():
    if shutil.which("alacritty"):
        return "alacritty"
    elif shutil.which("gnome-terminal"):
        return "gnome-terminal"
    else:
        raise Exception("No supported terminal emulator found (Alacritty or Gnome Terminal).")

# Function to open a new terminal with a given command
def open_terminal(command):
    terminal = detect_terminal()
    if terminal == "alacritty":
        process = subprocess.Popen(['alacritty', '-e', 'bash', '-c', command])
    elif terminal == "gnome-terminal":
        process = subprocess.Popen(['gnome-terminal', '--', 'bash', '-c', command])
    pids.append(process.pid)
    return process.pid

# Function to launch all servers and the client
def launch_processes():
    global pids
    pids = []  # Reset the PIDs list
    print("Launching servers and client...")
    for args in server_args:
        server_command = f'{server_base_command}{args}'
        open_terminal(server_command)
    open_terminal(client_command)
    print("All terminals have been launched.")

# Function to kill all spawned terminals
def kill_all_terminals():
    for pid in pids:
        try:
            # Kill the entire process group
            os.killpg(os.getpgid(pid), signal.SIGTERM)
            print(f"Terminated process group with PID: {pid}")
        except ProcessLookupError:
            print(f"Process with PID {pid} no longer exists.")
    pids.clear()  # Clear the PIDs list after killing processes

# Detect and print the terminal being used
terminal = detect_terminal()
print(f"Using {terminal} as the terminal emulator.")

# Launch processes initially
launch_processes()

# Keep the original terminal open and provide options
while True:
    user_input = input(
        "Type 'kill' to terminate all spawned terminals, 'restart' to kill and restart, or 'exit' to quit: "
    ).strip().lower()

    if user_input == 'kill':
        kill_all_terminals()
        print("All spawned terminals have been terminated.")
    elif user_input == 'restart':
        kill_all_terminals()
        time.sleep(1)  # Give some time for processes to terminate
        launch_processes()
    elif user_input == 'exit':
        print("Exiting without killing terminals.")
        break
    else:
        print("Invalid input. Please type 'kill', 'restart', or 'exit'.")