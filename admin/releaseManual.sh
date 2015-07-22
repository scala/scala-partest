#!/bin/bash

set -e

adminDir=$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )
cd "$adminDir/.."
repositoryName=$(basename `pwd`)

if [[ ! -f ~/.ivy2/.credentials ]]; then
  echo "In order to publish to sonatype, you need a credentials file ~/.ivy2/.credentials with the following content:"
  cat <<EOF
realm=Sonatype Nexus Repository Manager
host=oss.sonatype.org
user=<USERNAME>
password=<PASSWORD>
EOF
  exit 1
fi

function checkValidVersion() {
  if [[ "$1" == "" ]]; then
    echo "⚠️  Empty input, aborting."
    exit 0
  elif [[ ! "$1" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9-]+)? ]]; then
    echo "❌  Invalid version: $1. Aborting"
    exit 1
  fi
}

function confirmContinue() {
  local continueConfirmation="n"
  read -p "Continue (y/n)? " continueConfirmation
  if [[ "$continueConfirmation" != "y" ]]; then
    echo "⚠️  Aborting."
    exit 0
  fi
}

if [[ -n $(git status -s) ]]; then
  echo "⚠️  There are uncommited changes, make sure this is what you want."
  git status
  confirmContinue
fi

headTag=$(git describe --tags --exact-match 2> /dev/null || :)
if [[ "$headTag" =~ ^v[0-9]+\.[0-9]+\.[0-9]+(-[A-Za-z0-9-]+)? ]]; then
  releaseVersion=$(echo $headTag | sed -e s/^v//)
  echo "💡  The current HEAD is at tag $headTag. The release version is set to $releaseVersion."
else
  echo "⚠️  The current HEAD does not correspond to a tag."
  read -p "Enter a version number to build a release from the current HEAD (leave empty to abort): " releaseVersion
  checkValidVersion $releaseVersion
fi

setVersion='set every version := "'$releaseVersion'"'

echo "💡  Please specify the Scala version for building the release."
read -p "Enter '+' to cross-build against all versions in build.sbt's 'crossScalaVersions': " scalaVersionInput

if [[ "$scalaVersionInput" == "+" ]]; then
  testTarget="+test"
  publishTarget="+publish-signed"
else
  checkValidVersion $scalaVersionInput
  testTarget="test"
  publishTarget="publish-signed"
  setScalaVersion='set every scalaVersion := "'$scalaVersionInput'"'
fi

# ignore non-matching lines according to http://stackoverflow.com/questions/1665549/have-sed-ignore-non-matching-lines#comment19412026_1665662
javaVersion=`java -version 2>&1 | sed -e 's/java version "\(.*\)"/\1/' -e 'tx' -e 'd' -e ':x'`

if [[ "$scalaVersionInput" == "+" ]]; then
  scalaVersionInfo=" using the Scala versions in build.sbt's 'crossScalaVersions'"
else
  echo "💡  The current Java version is $javaVersion. Make sure to use 1.6 for Scala <2.12, 1.8 for Scala >=2.12."
  scalaVersionInfo=" using Scala $scalaVersionInput"
fi

echo "⚠️  About to release $repositoryName version $releaseVersion$scalaVersionInfo on Java $javaVersion."
confirmContinue

cp admin/gpg.sbt ./project
cp admin/publish-manual-settings.sbt .

echo "Running: sbt \"$setScalaVersion\" \"$setVersion\" clean update $testTarget $publishTarget"
sbt "$setScalaVersion" "$setVersion" clean update $testTarget $publishTarget

rm ./project/gpg.sbt
rm ./publish-manual-settings.sbt
