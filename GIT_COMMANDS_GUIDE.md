# Git Commands Reference Guide

A concise cheat sheet of the Git commands used to initialize, configure, align, commit, and push this repository.

---

| Command | One-Liner Explanation |
| :--- | :--- |
| `git init` | Initializes a brand-new local Git repository in the current project directory. |
| `git branch -M main` | Renames the default/initial working branch to `main`. |
| `git add .` | Stages all new, modified, and untracked files into the Git index. |
| `git status` | Displays the state of the working directory and staged changes ready for commit. |
| `git commit -m "<message>"` | Records staged snapshots to the local commit history with a descriptive message. |
| `git remote add origin <url>` | Connects the local repository to a remote GitHub repository named `origin`. |
| `git remote -v` | Lists all configured remote repository URLs for fetching and pushing. |
| `git fetch <url> main` | Downloads commits, files, and refs from the remote branch without merging into local files. |
| `git diff FETCH_HEAD..main --stat` | Compares changes between the fetched remote branch and the local branch. |
| `git reset --soft FETCH_HEAD` | Resets commit history to match remote HEAD while keeping all local code changes staged. |
| `git push <url> main:main` | Uploads local `main` branch commits to the remote repository on GitHub. |
| `git remote set-url origin <url>` | Updates the remote repository URL to remove sensitive tokens from local Git config. |
| `git log -n <count>` | Displays a log of the most recent commits in the repository. |
