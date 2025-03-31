
#!/usr/bin/env python3
import subprocess
import os
import time
import shutil
import platform

# Define the install command
# after the installation, wait for user input to continue
install_command = 'mvn clean install'
# Define the base command for the server
server_base_command = 'mvn exec:java -Dexec.mainClass="depchain.blockchain.BlockchainMember" -Dexec.args='
# Define the base command for the client
client1_command = 'mvn exec:java -Dexec.mainClass="depchain.client.DepChainClient" -Dexec.args="5 9001"'
client2_command = 'mvn exec:java -Dexec.mainClass="depchain.client.DepChainClient" -Dexec.args="6 9002"'
client3_command = 'mvn exec:java -Dexec.mainClass="depchain.client.DepChainClient" -Dexec.args="7 9003"'
# Define the server arguments
server_args = [
    '"1 8001"',
    '"2 8002"',
    '"3 8003"',
    '"4 8004"'
]

# Session name for the main tmux session
session_name = "depchain-session"

# Check if tmux is installed
def check_tmux():
    if not shutil.which("tmux"):
        raise Exception("tmux is not installed. Please install tmux to use this script.")

# Detect if running in WSL
def is_wsl():
    # Check if /proc/version contains Microsoft or WSL
    if os.path.exists('/proc/version'):
        with open('/proc/version', 'r') as f:
            return 'microsoft' in f.read().lower() or 'wsl' in f.read().lower()
    return False

# Detect available terminal emulator
def detect_terminal():
    wsl_mode = is_wsl()
    
    if wsl_mode:
        # In WSL, we'll use tmux directly without launching a separate terminal
        return "wsl"
    elif shutil.which("alacritty"):
        return "alacritty"
    elif shutil.which("gnome-terminal"):
        return "gnome-terminal"
    else:
        # If no known terminal is found and not in WSL, use a fallback
        print("No supported terminal emulator found. Using tmux in the current terminal.")
        return "fallback"

# Create a tmux session with all necessary panes
def create_tmux_layout():
    # Check if the session already exists and kill it
    result = subprocess.run(['tmux', 'has-session', '-t', session_name], 
                           stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    
    if result.returncode == 0:
        # Kill the existing session to start fresh
        subprocess.run(['tmux', 'kill-session', '-t', session_name])
    
    # Create a new session with the first pane
    subprocess.run(['tmux', 'new-session', '-d', '-s', session_name, '-n', 'depchain'])
    
    # Set up a more reliable layout
    # First create a 2x2 grid for servers
    subprocess.run(['tmux', 'split-window', '-v', '-t', f'{session_name}:0.0'])
    subprocess.run(['tmux', 'split-window', '-v', '-t', f'{session_name}:0.0'])
    subprocess.run(['tmux', 'split-window', '-h', '-t', f'{session_name}:0.1'])
    # Now add a single bottom pane for clients
    subprocess.run(['tmux', 'split-window', '-v', '-t', f'{session_name}:0.3'])
    # Split the bottom pane into 3 horizontal panes for clients
    subprocess.run(['tmux', 'split-window', '-h', '-t', f'{session_name}:0.4'])
    subprocess.run(['tmux', 'split-window', '-h', '-t', f'{session_name}:0.5'])
    

# Function to launch all processes in the panes
def launch_processes():
    print("Launching servers and client in tmux panes...")
    
    # First, send the install command to the client pane
    client1_target = f'{session_name}:0.4'  # This should be the 5th pane
    subprocess.run(['tmux', 'send-keys', '-t', client1_target, install_command, 'Enter'])
    
    client2_target = f'{session_name}:0.5'  # This should be the 6th pane
    subprocess.run(['tmux', 'send-keys', '-t', client2_target, install_command, 'Enter'])
    
    client3_target = f'{session_name}:0.6'  # This should be the 7th pane
    subprocess.run(['tmux', 'send-keys', '-t', client3_target, install_command, 'Enter'])
    
    # Wait for the installation to complete by checking if the process is still running
    print("Waiting for installation to complete...")
    
    # Method 1: Poll the pane for completion
    def is_process_running():
        for target in [client1_target, client2_target, client3_target]:
            result = subprocess.run(
                ['tmux', 'capture-pane', '-p', '-t', target], 
                capture_output=True, 
                text=True
            )

            if "ERROR" in result.stdout:
                print(f"Build failed in {target}. Exiting...")
                return False  # Exit the script

            if "BUILD SUCCESS" in result.stdout:
                return False  # Exit polling

        return True  # Keep polling
        
    # Simple polling loop
    while is_process_running():
        time.sleep(1)  # Wait a second between checks
    
    # Now proceed with launching servers
    for i, args in enumerate(server_args):
        server_command = f'{server_base_command}{args}'
        target = f'{session_name}:0.{i}'
        
        # Label and run command in the pane
        subprocess.run(['tmux', 'send-keys', '-t', target, 'clear', 'Enter'])
        subprocess.run(['tmux', 'send-keys', '-t', target, f'echo "Server {i+1} (Port: 800{i+1})"', 'Enter'])
        subprocess.run(['tmux', 'send-keys', '-t', target, server_command, 'Enter'])
    
    # Launch client1 process in the second last pane
    subprocess.run(['tmux', 'send-keys', '-t', client1_target, 'echo "Client (Port: 9001)"', 'Enter'])
    subprocess.run(['tmux', 'send-keys', '-t', client1_target, client1_command, 'Enter'])
    
    # Launch client2 process in the last pane
    subprocess.run(['tmux', 'send-keys', '-t', client2_target, 'echo "Client (Port: 9002)"', 'Enter'])
    subprocess.run(['tmux', 'send-keys', '-t', client2_target, client2_command, 'Enter'])
    
    # Launch client3 process in the last pane
    subprocess.run(['tmux', 'send-keys', '-t', client3_target, 'echo "Client (Port: 9003)"', 'Enter'])
    subprocess.run(['tmux', 'send-keys', '-t', client3_target, client3_command, 'Enter'])
    
    
    print("All processes have been launched in tmux panes.")

# Function to interrupt all running processes
def interrupt_processes():
    print("Interrupting all running processes...")
    
    # Send Ctrl+C to all panes
    for i in range(7):  # 4 servers + 3 client = 7 panes
        target = f'{session_name}:0.{i}'
        subprocess.run(['tmux', 'send-keys', '-t', target, 'C-c', 'C-c'])
    
    # Give processes time to terminate
    time.sleep(1)
    
    print("All processes have been interrupted.")

# Function to kill the tmux session
def kill_session():
    print("Terminating tmux session...")
    
    result = subprocess.run(['tmux', 'has-session', '-t', session_name],
                           stderr=subprocess.PIPE, stdout=subprocess.PIPE)
    
    if result.returncode == 0:
        subprocess.run(['tmux', 'kill-session', '-t', session_name])
    
    print("Tmux session has been terminated.")

# Open the terminal with the tmux session
def open_terminal_with_tmux():
    terminal = detect_terminal()

    # Enable mouse support in tmux
    subprocess.run(['tmux', 'set-option', '-g', 'mouse', 'on'])
    
    if terminal == "wsl":
        # In WSL, we can attach directly to the tmux session
        print("WSL detected. Please run 'tmux attach-session -t depchain-session' in another terminal window.")
        print("You can also view the session from this terminal after closing this control interface.")
    elif terminal == "alacritty":
        subprocess.Popen(['alacritty', '-e', 'tmux', 'attach-session', '-t', session_name])
    elif terminal == "gnome-terminal":
        subprocess.Popen(['gnome-terminal', '--', 'tmux', 'attach-session', '-t', session_name])
    else:  # fallback
        print(f"A tmux session '{session_name}' has been created.")
        print(f"To view it, open another terminal and run: tmux attach-session -t {session_name}")

# Main execution
check_tmux()
create_tmux_layout()
open_terminal_with_tmux()
launch_processes()

# Keep the original terminal open and provide options
while True:
    user_input = input(
        "Type 'restart' to interrupt and restart, 'kill' to terminate session and quit, or 'attach' to view session in this terminal: "
    ).strip().lower()
    
    if user_input == 'restart':
        interrupt_processes()
        time.sleep(1)  # Give time for processes to terminate
        launch_processes()
    elif user_input == 'kill':
        kill_session()
        break
    elif user_input == 'attach':
        # Allow attaching to the session from the current terminal
        os.system(f'tmux attach-session -t {session_name}')
        print("Returned to control interface. Sessions are still running.")
    else:
        print("Invalid input. Please type 'restart', 'kill', or 'attach'.")
