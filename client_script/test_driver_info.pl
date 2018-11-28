#!/usr/bin/env perl

use warnings FATAL => 'all';
use File::Slurp qw/read_file/;
use Data::Dumper;
use XML::Simple;

sub getDriverInfo {
    my $systemInfo = read_file("testdata.txt");

    my $data = {};

    my $currentSection;
    foreach(split(/\n/, $systemInfo)){
        $currentSection=$1 if(/^\s*(ATTO.*):$/);
        $currentSection=undef if(defined $currentSection and /^\s*$/ and defined $data->{$currentSection}->{"Version"});
        if(defined $currentSection) {
            $data->{$currentSection}->{"Version"} = $1 if (/^\s*Version: (.*)$/);
            $data->{$currentSection}->{"BundleID"} = $1 if (/^\s*Bundle ID: (.*)$/);
            $data->{$currentSection}->{"Version"} = $1 if (/^\s*Version: (.*)$/);
            $data->{$currentSection}->{"Loaded"} = $1 if (/^\s*Loaded: (.*)$/);
            $data->{$currentSection}->{"GetInfoString"} = $1 if (/^\s*Get Info String: (.*)$/);
            $data->{$currentSection}->{"ObtainedFrom"} = $1 if (/^\s*Obtained From: (.*)$/);
            $data->{$currentSection}->{"Kind"} = $1 if (/^\s*Kind: (.*)$/);
            $data->{$currentSection}->{"Architecture"} = $1 if (/^\s*Architecture: (.*)$/);
            $data->{$currentSection}->{"Location"} = $1 if (/^\s*Location: (.*)$/);
            $data->{$currentSection}->{"KextVersion"} = $1 if (/^\s*Kext Version: (.*)$/);
            $data->{$currentSection}->{"Loadable"} = $1 if (/^\s*Loadable: (.*)$/);
            $data->{$currentSection}->{"Dependencies"} = $1 if (/^\s*Dependencies: (.*)$/);
            $data->{$currentSection}->{"SignedBy"} = $1 if (/^\s*Signed by: (.*)$/);
        }
    }

    my $arrayOutput=[];

    foreach(keys %$data){
        my $updatedHash = $data->{$_};
        $updatedHash->{"DriverName"} = $_;
        push @$arrayOutput, $updatedHash;
    }
    return $arrayOutput;
}

my $result = getDriverInfo;
print Dumper($result);
print XMLout({"driver"=>$result}, RootName=>"drivers");