# OPSIN

## Origin
The code is cloned from https://bitbucket.org/dan2097/opsin/

### Branches
`upstream` branch on GitHub is for original code
`master` branch on GitHub is for debianized version

### Fetching changes from upstream
To be able to merge original upstream into `upstream` branch:

0. Clone the repo from GitHub and switch to `upstream` branch:
```
git clone https://github.com/SureChEMBL/opsin.git
cd opsin
git checkout upstream
```

1. Install [git-remote-hg][https://github.com/felipec/git-remote-hg]
```
sudo apt install git-remote-hg
```

2. Add new remote:
```
git remote add upstream hg::https://bitbucket.org/dan2097/opsin
```

3. Fetch the latest changes:
```
git fetch upstream
```

4. Merge into local `upstream` version:
```
git merge upstream/master
```

5. Push back to GitHub:
```
git push origin upstream
```


## Dependency on `jaxen`
OPSIN depends on `xom`, but `libxom-java` package in Ubuntu/Debian doesn't list `jaxen` in POM dependencies, though `libjaxen-java` is presented in Debian dependencies. So support of `jaxen` in `libxom-java` is considered as optional and you need to include dependency on `jaxen` in your own project if needed.

For this reason `libopsin-java` depends on `libjaxen-java` and list this dependency in main `pom.xml` and in `opsin-core/pom.xml`.


## Package version
The original `libopsing-java` package from Ubuntu/Debian doesn't install the library into `/usr/share/maven-repo`. In order to distinguish original version and the version used by SureChEMBL, the version has `+maven` suffix.
