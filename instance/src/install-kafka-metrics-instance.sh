#!/usr/bin/env bash

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source "$DIR/kafka-metrics-common.sh"

export GOPATH="$INSTALL_DIR/golang"

install_influxdb() {
    echo "Installing latest InfluxDB..."
    cd "$GOPATH/src"
    go get github.com/influxdata/influxdb
    cd $GOPATH/src/github.com/influxdata/
    go get ./...
    go install ./...
}

install_grafana() {
    echo "Installing latest Grafana..."
    cd "$GOPATH/src"
    go get github.com/grafana/grafana
    cd $GOPATH/src/github.com/grafana/grafana
    go run build.go setup              # (only needed once to install godep)
    $GOPATH/bin/godep restore          # (will pull down all golang lib dependencies in your current GOPATH)
    go run build.go build
    npm install
    npm install -g grunt-cli
    grunt --force
}

ensure_golang() {
    go version
    if [ ! $? -eq 0 ]; then
        case "$OSTYPE" in
          darwin*) URL="https://storage.googleapis.com/golang/go1.5.2.darwin-amd64.tar.gz" ;;
          linux*) URL="https://storage.googleapis.com/golang/go1.5.2.linux-amd64.tar.gz" ;;
          bsd*) URL="https://storage.googleapis.com/golang/go1.5.2.freebsd-amd64.tar.gz" ;;
          *) URL="" ;;
        esac
        if [ ! -z "$URL" ]; then
            GOPACKAGE="$DOWNLOAD_DIR/$(basename $URL)"
            if [ ! -f "$GOPACKAGE" ]; then
                download $URL "$GOPACKAGE"
                tar -C /usr/local -xzf "$GOPACKAGE"
            fi
            export PATH=$PATH:/usr/local/go/bin
#            TODO echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.profile
#            TODO echo 'export PATH=$PATH:/usr/local/go/bin' >> ~/.bash_profile
        fi
        go version
        if [ ! $? -eq 0 ]; then
            echo "Failed to install GoLang on your system `$OSTYPE` - please try manually";
            exit 1;
        fi
    fi
    if [ ! -d $DEST_DIR ]; then
        mkdir -p $DEST_DIR
    fi
}

ensure_golang
install_influxdb
install_grafana