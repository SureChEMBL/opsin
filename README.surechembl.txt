The code is cloned from https://bitbucket.org/dan2097/opsin/

'upstream' branch on GitHub is for original code
'master' branch on GitHub is for debianized version


To be able to merge original upstream into 'upstream' branch:

0. Clone the repo from GitHub and switch to 'upstream' branch:
$ git clone https://github.com/SureChEMBL/opsin.git
$ cd opsin
$ git checkout upstream

1. Install git-remote-hg https://github.com/felipec/git-remote-hg
$ sudo apt install git-remote-hg).

2. Add new remote:
$ git remote add upstream hg::https://bitbucket.org/dan2097/opsin

3. Fetch the latest changes:
$ git fetch upstream

4. Merge into local 'upstream' version:
$ git merge upstream/master

5. Push back to GitHub:
$ git push origin upstream
