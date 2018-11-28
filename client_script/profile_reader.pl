#!/usr/bin/env perl

use warnings;
use Data::Dumper;
use XML::Simple;
use LWP::UserAgent;
use Getopt::Long;
use XML::Parser;

my $format="text";
my $useXml;
my $outputUri;
GetOptions("xml"=>\$useXml, "out=s"=>\$outputUri);


$format="xml" if($useXml);

my $hostname = `hostname`;
chomp $hostname;

sub getIpAddresses {
  my $systemInfo = `system_profiler SPNetworkDataType`;

  my @addresses;
  foreach(split(/\n/,$systemInfo)){
    if(/IPv4 Addresses:\s*(.*)$/){
      chomp $1;
      push @addresses,$1;
    }
  }

  return @addresses;
}

sub getFibreInfo {
  my $systemInfo = `system_profiler SPFibreChannelDataType`;
  my $domainId=-1;
  my $data = {};

  foreach(split(/\n/,$systemInfo)){
    $domainId=$1 if(/^\s+Fibre Channel Domain (\d+)/);
    $data->{"Domain_$domainId"}->{"lunCount"}=0 if($domainId>=0 and not defined $data->{"Domain_$domainId"});

    $data->{"Domain_$domainId"}->{"Status"}=$1 if(/^\s+Status: (.*)$/);
    $data->{"Domain_$domainId"}->{"WWN"}=$1 if(/^\s+Port World Wide Name: (.*)$/);
    $data->{"Domain_$domainId"}->{"Speed"}=$1 if(/^\s+Speed: (.*)$/);
    ++($data->{"Domain_$domainId"}->{"lunCount"}) if(/^\s+SCSI Logical Unit (.*)$/);

    $data->{"Product"}=$1 if(/^\s+Product: (.*)$/);
  }

  return $data;
}

sub getHardwareInfo {
    my $hwInfo = `system_profiler SPHardwareDataType`;

    my $data = {};

    foreach(split(/\n/,$hwInfo)){
        $data->{"model"} = $1 if(/^\s+Model Name: (.*)\s*$/);
        $data->{"hw_uuid"} = $1 if(/^\s+Hardware UUID: (.*)\s*$/);
    }
    return $data;
}

sub getComputerName {
  my $systemInfo = `system_profiler SPSoftwareDataType`;

  foreach(split(/\n/,$systemInfo)){
    return $1 if(/^\s+Computer Name: (.*)$/);
  }
}

sub getDriverInfo {
    my $systemInfo = `system_profiler SPExtensionsDataType`;

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

sub breakdownTimespec {
    my ($timespec) = @_;

    if($timespec=~/(\d+):(\d+)/){
        return {days=>0,hours=>$1,minutes=>$2}
    } elsif($timespec=~/(\d+)\+(\d+):(\d+)/){
        return {days=>$1,hours=>$2,minutes=>$3}
    }
}

sub breakdownLongDate {
    my ($datespec) = @_;

    if($datespec=~/^\s*([^\-]+)\s/){
        return $1;
    }
}

sub getLoginHistory {
    my $rawHistory = `last | grep console | grep -v localhome |  head`;
    my @result;

    foreach(split(/\n/, $rawHistory)){
        if(/^([\w_]+)\s+([\w\d\.]+)\s+([^\(]+)\s*\(([\d+:]+)\)/) {
            push @result, { "hostname"=>$hostname, "username" => $1, "location" =>$2, login=>breakdownLongDate($3), duration=>breakdownTimespec($4)}
        }
    }
    return \@result;
}


sub checkDenyDlc {
    return [] if(! -f "/Library/Preferences/com.apple.xsan.plist");
    my $xsanContent = `plutil -convert xml1 /Library/Preferences/com.apple.xsan.plist -o - | grep denyDLC -A 10`;
    return [] if($? != 0);   #text was not found
    my @result;

    foreach(split(/\n/, $xsanContent)){
        push @result, $1 if(/^\s+<string>(.*)<\/string>/);
        last if(/^\s+<\/array>/)
    }
    return \@result;
}

sub printHash {
  my ($data,$indentLevel,$withComma)=@_;
  my $indent="";
  $indent = "$indent\t" for(1..$indentLevel);

  my $nKeys = scalar (keys %$data);
  my $n=0;
  print "$indent\{";
  my $nextWithComma = $n!=$nKeys;

  foreach(keys %$data){
    ++$n;
    if(ref $data->{$_} eq "HASH"){
      print "$indent\"$_\": ";
      printHash($data->{$_},$indentLevel+1,$nextWithComma);
    } elsif(ref $data->{$_} eq "ARRAY"){
      die "ARRAY output not implemented";
    } else {
      printkv($_,$data->{$_},$indent,1,$nextWithComma);
    }
  }
  print "$indent\}";
  if($withComma){
    print ",\n";
  } else {
    print "\n";
  }
}

sub printkv {
  my($k,$v,$indent, $quote,$withComma)=@_;
  my $end;
  if($withComma){
    $end=",";
  } else {
    $end="";
  }

  if($quote){
    print "$indent\"$k\":\"$v\"$end\n";
  } else {
    print "$indent\"$k\":$v,\n";
  }
}

print "Collecting computer name...\n";
my $computerName = getComputerName;
print "Collecting IP addresses...\n";
my @ipInfo = getIpAddresses;
print "Collecting fibrechannel info...\n";
my $fibreInfo = getFibreInfo;
print "Collecting login history...\n";
my $recentLogins = getLoginHistory;
print "Collecting hardware info...\n";
my $hwInfo = getHardwareInfo;
print "Collecting DLC status...\n";
my $denyDlc = checkDenyDlc;
print "Collecting fibre drivers info...\n";
my $driverInfo = getDriverInfo;

if($format eq "text"){
  print "Report for $hostname ($computerName)\n\n";
  print "IP Addresses:\n";
  print "\t$_\n" foreach(@ipInfo);
  print "Fibrechannel:\n";
  print Dumper($fibreInfo);
    print "Recent logins:\n";
    print Dumper($recentLogins);
    print "Hardware info:\n";
    print Dumper($hwInfo);
    print "Driver info:\n";
    print Dumper($driverInfo);
    print "DenyDLC run:\n";
    print Dumper($denyDlc);
} elsif($format eq "xml"){
  my $data = {
    "hostname"=>$hostname,
    "computerName"=>$computerName,
    "ipAddresses"=>\@ipInfo,
    "fibrechannel"=>$fibreInfo,
      "model"=>$hwInfo->{"model"},
      "hw_uuid"=>$hwInfo->{"hw_uuid"},
      "denyDlc"=>{"volume"=>$denyDlc},
      "driverInfo"=>{"driver"=>$driverInfo}
  };

    my $content = XMLout($data,RootName=>"data", KeyAttr=>[ "model","hw_uuid","computerName","denyDlc"]);
    my $loginsContent = XMLout({recentLogins=>$recentLogins},RootName=>"logins", KeyAttr => "recentLogin");

    if($outputUri){
        my $ua=LWP::UserAgent->new;
        $ua->timeout(30);
        $ua->env_proxy;
        my $response=$ua->post("$outputUri/api/hostinfo", Content=>$content, "Content-Type"=>"application/xml");
        if($response->is_success){
            print "Sent report to $outputUri\n"
        } else {
            print $content . "\n";
            print "Could not send report to $outputUri: ".$response->decoded_content;
        }
        my $loginresponse =$ua->post("$outputUri/api/logins", Content=>$loginsContent, "Content-Type"=>"application/xml");
        if($loginresponse->is_success){
            print "Sent report to $outputUri"
        } else {
            print $content . "\n";
            print "Could not send report to $outputUri: ".$loginresponse->decoded_content;
        }
    } else {
        print $content . "\n";
        print $loginsContent . "\n";
    }
}
