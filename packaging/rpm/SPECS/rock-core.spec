Name:           rock-core
Version:        1.0.0
Release:        1%{?dist}
Summary:        Minecraft Bedrock Dedicated Server manager

License:        GPL-3.0-or-later
URL:            https://github.com/krxoid/rock-core

Requires:       java
BuildArch:      noarch

%description
Command-line manager for Minecraft Bedrock Dedicated Server instances,
written in Java.

%prep

%build

%install
install -D -m 644 \
    /home/krxoid/IdeaProjects/rock-core/build/libs/rock-core-1.0-RELEASE.jar \
    %{buildroot}%{_datadir}/rock-core/rock-core.jar

install -D -m 755 \
    /home/krxoid/IdeaProjects/rock-core/packaging/rpm/rock \
    %{buildroot}%{_bindir}/rock

%files
%{_bindir}/rock
%{_datadir}/rock-core/rock-core.jar
