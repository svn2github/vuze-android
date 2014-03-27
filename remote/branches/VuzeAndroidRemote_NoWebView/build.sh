#!/bin/bash
if [ "$1" == "" ]; then
	BUILDTYPE=debug
elif [ "$1" == "releaseagain" ]; then
	BUILDTYPE=release
else
	BUILDTYPE=$1
fi

SRCDIR=`pwd`

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
rm -rf "${DSTDIR}"

echo Copying ${SRCDIR} to ${DSTDIR}

cp -r "${SRCDIR}" "${DSTDIR}"
sed -i .bk -e "s/false/true/g" "${DSTDIR}/res/values/analytics.xml"
sed -i .bk -e "s/DEBUG = true/DEBUG = false/g" "${DSTDIR}/src/com/vuze/android/remote/AndroidUtils.java"
rm -r "${DSTDIR}/src/com/vuze/android/remote/AndroidUtils.java.bk"
rm -r "${DSTDIR}/src/com/aelitis/azureus/util/JSONUtilsGSON.java"
rm -r "${DSTDIR}/libs/gson-2.2.4.jar"
rm -r "${DSTDIR}/src/com/aelitis/azureus/util/ObjectTypeAdapterLong.java"
rm -r "${DSTDIR}/bin/"*
rm -r "${DSTDIR}/gen/"*
find "${DSTDIR}" -name '.svn' -exec rm -rf {} \;
find "${DSTDIR}/src" -name '*.txt' -exec rm -rf {} \;
cd "${DSTDIR}"
android update project --name VuzeAndroidRemote --path .
ant clean
ant ${BUILDTYPE}
cp -f "${DSTDIR}/bin/VuzeAndroidRemote-release.apk" ../VuzeAndroidRemote-${NEXTVER}.apk
tar -czf "${DSTDIR}.tar.gz" .
