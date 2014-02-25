#!/bin/perl

use XML::Parser;

my $file = shift;

my $buf = "";
my $p = XML::Parser->new(Handlers => {
    Start => sub {
        my($expat,$element) = @_;
        if ($element eq 'abstract') {
            $buf='';
        }
    },
    End   => sub {
        my($expat,$element) = @_;
        if ($element eq 'abstract') {
            $buf =~ s/[\n\r]//g;
            print "$buf\n" if $buf =~ /^\w/;
        } 
    },
    Char  => sub {
        my($expat,$str) = @_;
        $buf .= $str;
    },
});

$p->parsefile($file);
