#!/bin/bash
if [ "$1" == "" ]; then
	BUILDTYPE=debug
elif [ "$1" == "releaseagain" ]; then
	BUILDTYPE=release
else
	BUILDTYPE=$1
fi

SRCDIR=`pwd`
if [[ "$SRCDIR" == *VuzeRemoteProject ]]; then
	echo build.sh does not need to be called.  Just use ant.
	exit
fi
BUILDS_DIR=`cd ${SRCDIR}/../builds;pwd`
GITDIR=/Volumes/Workspace/git

ver=$(sed -n 's/.*android:versionCode="\([^"+]*\)".*/\1/p' ${SRCDIR}/AndroidManifest.xml)
if [ "$1" == "releaseagain" ]; then
	NEXTVER=$ver
else
	NEXTVER=$(($ver +1))
	sed -i .bk -e "s/android:versionCode=\"${ver}\"/android:versionCode=\"${NEXTVER}\"/g" ${SRCDIR}/AndroidManifest.xml 
	sed -i .bk -e "s/Alpha ${ver}/Alpha ${NEXTVER}/g" ${SRCDIR}/AndroidManifest.xml 
	rm ${SRCDIR}/AndroidManifest.xml.bk
fi

DSTDIR=${SRCDIR}-${NEXTVER}
DSTDIR_APP=${DSTDIR}/VuzeRemoteProject
ARCHIVE_DIR=`cd $(dirname $DSTDIR)/old;pwd`

echo SRCDIR=${SRCDIR}
echo DSTDIR=${DSTDIR}
echo DSTDIR_APP=${DSTDIR_APP}
echo ARCHIVE_DIR=${ARCHIVE_DIR}
echo BUILDS_DIR=${BUILDS_DIR}

rm -rf "${DSTDIR}"

echo Copying ${SRCDIR} to ${DSTDIR_APP}
mkdir ${DSTDIR}
cp -r "${SRCDIR}" "${DSTDIR_APP}"
cp -r "${SRCDIR}/../android-pull-to-refresh" "${DSTDIR}"
cp -r "${SRCDIR}/../PagerSlidingTabStrip" "${DSTDIR}"
cp -r "${SRCDIR}/../appcompat" "${DSTDIR}"
cp -r "${GITDIR}/android-switch-backport" "${DSTDIR}"
cp -r "${GITDIR}/MaterialEditText" "${DSTDIR}"

echo Replacing Text, Cleaning up unneeded files
sed -i .bk -e "s/=..\/..\/git\//=..\//g" "${DSTDIR_APP}/project.properties"
if [ "$1" != "debug" ]; then
	sed -i .bk -e "s/DEBUG = true/DEBUG = false/g" "${DSTDIR_APP}/src/com/vuze/android/remote/AndroidUtils.java"
	sed -i .bk -e "s/DEBUG_MENU = true/DEBUG_MENU = false/g" "${DSTDIR_APP}/src/com/vuze/android/remote/AndroidUtils.java"
	rm -r "${DSTDIR_APP}/src/com/vuze/android/remote/AndroidUtils.java.bk"
fi
find "${DSTDIR_APP}" -name '.svn' -exec rm -rf {} \;
find "${DSTDIR_APP}/src" -name '*.txt' -exec rm -rf {} \;

echo Updating Projects
cd "${DSTDIR}/appcompat"
android update project --path .
cd "${DSTDIR}/android-pull-to-refresh"
sed -i .bk -e "s/=..\/..\/..\/..\/..\/workspace-droid/=../g" "project.properties"
android update project --path .
cd "${DSTDIR}/PagerSlidingTabStrip"
sed -i .bk -e "s/=..\/..\/..\/..\/..\/workspace-droid/=../g" "project.properties"
android update project --path .
cd "${DSTDIR}/MaterialEditText/library/src/main"
sed -i .bk -e "s/=..\/..\/..\/..\/..\/workspace-droid/=..\/..\/..\/../g" "project.properties"
android update project --path .
cd "${DSTDIR}/android-switch-backport/library"
sed -i .bk -e "s/=..\/..\/..\/..\/..\/workspace-droid/=..\/../g" "project.properties"
android update project --path .
cd "${DSTDIR_APP}"
android update project --name VuzeAndroidRemote --path .

echo Clean
ant clean > /dev/null

echo Build
ant ${BUILDTYPE}
cp -f "${DSTDIR_APP}/bin/VuzeAndroidRemote-release.apk" ${BUILDS_DIR}/VuzeAndroidRemote-${NEXTVER}.apk
mv "${DSTDIR_APP}/bin/proguard" "${DSTDIR_APP}/"
ant clean > /dev/null

cd ${DSTDIR}
rm -rf `find . -name .svn`
rm -rf `find . -name .git`
rm -rf `find . -name .gitignore`
rm -rf `find . -name .DS_Store`
echo Compressing source and move to ${BUILDS_DIR}
tar -czf "${DSTDIR}.tar.gz" *
mv -fv "${DSTDIR}.tar.gz" ${BUILDS_DIR}

echo Moving $DSTDIR to $ARCHIVE_DIR
cp -R $DSTDIR ${ARCHIVE_DIR}
rm -rf $DSTDIR
cd ${SRCDIR}
