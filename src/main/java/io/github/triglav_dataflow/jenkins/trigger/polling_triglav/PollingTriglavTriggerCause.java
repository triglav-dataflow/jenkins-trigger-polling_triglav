package io.github.triglav_dataflow.jenkins.trigger.polling_triglav;

import hudson.model.Cause;

public class PollingTriglavTriggerCause
        extends Cause // see. https://wiki.jenkins-ci.org/display/JENKINS/Root+Cause+Plugin
{
    @Override
    public String getShortDescription()
    {
        return "Started by PollingTriglavTrigger Plugin ┌(_Д_┌ )┐";
    }
}