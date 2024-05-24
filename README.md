# discovery

[![CircleCI](https://dl.circleci.com/status-badge/img/gh/Consensys/discovery/tree/master.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/Consensys/discovery/tree/master)
[![Latest version of 'discovery' @ Cloudsmith](https://api-prd.cloudsmith.io/v1/badges/version/consensys/maven/maven/discovery/latest/a=noarch;xg=tech.pegasys.discovery/?render=true&show_latest=true)](https://cloudsmith.io/~consensys/repos/maven/packages/detail/maven/discovery/latest/a=noarch;xg=tech.pegasys.discovery/)

## Overview

This is a Java implementation of the [Discovery v5](https://github.com/ethereum/devp2p/blob/master/discv5/discv5.md)
peer discovery protocol.

## Dependency

```groovy
repositories {
    maven { url "https://artifacts.consensys.net/public/maven/maven/" }
}

dependencies {
    implementation("tech.pegasys:discovery:<version>")
}
```
