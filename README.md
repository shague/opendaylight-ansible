# opendaylight-ansible

Travis-CI

## Executing Ansible Playbooks

Southbound Ansible execution may be directly invoked by using RPCs.
To do this you must provide the host, directory, and playbook file to execute.
To issue an RPC call to localhost, to run a `test.yaml` playbook in `/tmp`:

`curl -i -u admin:admin -X POST -H "Content-Type: application/json" 127.0.0.1:8181/restconf/operations/ansible-command:run-ansible-command -d '{"input": {"host":"localhost", "directory":"/tmp", "file":"test.yaml"}}'`

The RPC will then return a uuid of the command requested:

`{"output":{"status":"INPROGRESS","uuid":"2bb34fab-2e05-42f6-acec-ca0c5cf02ad2"}}`

This uuid can now be used to query the operational database to see the result of the Command:

`curl -u admin:admin 127.0.0.1:8181/restconf/operational/ansible-command:commands/command/2bb34fab-2e05-42f6-acec-ca0c5cf02ad2/`

`{"command":[{"uuid":"2bb34fab-2e05-42f6-acec-ca0c5cf02ad2","status":"COMPLETE"}]}`

In case of a failed command the output from the Ansible Task which failed will also be present:

`{"command":[{"uuid":"75ab1888-c742-46c3-9d77-5fdfe4b22df3","status":"FAILED","failed-event":"\u001b[0;31mfatal: [localhost]: FAILED! => {\"changed\": true, \"cmd\": \"alsjf\", \"delta\": \"0:00:00.002941\", \"end\": \"2018-08-30 15:56:00.770642\", \"failed\": true, \"rc\": 127, \"start\": \"2018-08-30 15:56:00.767701\", \"stderr\": \"/bin/sh: alsjf: command not found\", \"stderr_lines\": [\"/bin/sh: alsjf: command not found\"], \"stdout\": \"\", \"stdout_lines\": []}\u001b[0m"}]}`