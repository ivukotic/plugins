Instructions for building dCache xRootD Monitoring plugin
=========================================================

Getting the correct repositories
--------------------------------
In order to build the dCache xRootD monitoring plugin, you will first need to install the correct repositories for Apache Maven.


```
# cat << EOF > /etc/yum.repos.d/jpackage-generic.repo
name=JPackage generic free
mirrorlist=http://www.jpackage.org/mirrorlist.php?dist=generic&type=free&release=6.0
enabled=1
gpgcheck=1
gpgkey=http://www.jpackage.org/jpackage.asc

[jpackage-generic-devel]
name=JPackage Generic Developer
mirrorlist=http://www.jpackage.org/mirrorlist.php?dist=generic&type=devel&release=6.0
enabled=1
gpgcheck=1
gpgkey=http://www.jpackage.org/jpackage.asc
EOF
```

After adding the repo, you will need to `yum install maven3` as a build dependency of the plugin.

Installing the source RPM
-------------------------
You will need to install the RPM to your local rpmbuild directory as such:
```
rpm -ivh dcache-plugin-xrootd-monitor-5.0.0-0.src.rpm
```

Tagging a release
-----------------
If you want to build a release that is tagged with the short git commit hash, you will need to cd to the root directory of the plugins repository and do the following:
```
git archive --format tar --prefix=dcache-plugin-xrootd-monitor-$(date +%Y%m%d)git$(git rev-parse --short HEAD)/ HEAD monitor/ | gzip -9 > dcache-plugin-xrootd-monitor-src.$(date +%Y%m%d)git$(git rev-parse --short HEAD).tar.gz
```
Otherwise to release a proper versioned release:
```
export version=5.0.0; git archive --format tar --prefix=dcache-plugin-xrootd-monitor-$version/ HEAD monitor/ | gzip -9 > dcache-plugin-xrootd-monitor-src.$version.tar.gz
```

Once that is completed, copy the tarball to the RPM SOURCES directory:
```
cp dcache-plugin-xrootd-monitor-src.[YOUR-VERSION-HERE].tar.gz ~/rpmbuild/SOURCES/
```

Rebuilding the RPM
------------------
Once the source RPM is installed and the new sources have been copied into the rpmbuild directory, go ahead and edit the spec file:
```
vim ~/rpmbuild/SPECS/dcache-plugin-xrootd-monitor.spec
```

You will need to update the Version field to match the version you created via `git archive` above. If this doesnt match exactly, then the RPM build will fail.

Additionally, you should update the changelog to describe the changes you have made.

Finally, rebuild the RPM:
```
rpmbuild -bb --define "_binary_filedigest_algorithm  1"  --define "_binary_payload 1" dcache-plugin-xrootd-monitor.spec
```

The extra `--define` statements are needed to ensure that built RPMs work on EL5
